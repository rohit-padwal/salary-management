package com.acme.salarymanagement.security;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.acme.salarymanagement.SalaryManagementApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSecurityIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USERNAME = "test-hr";
    private static final String PASSWORD = "synthetic-test-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(10).encode(PASSWORD);
    private static ConfigurableApplicationContext context;
    private static URI baseUri;

    private CookieManager cookies;
    private HttpClient client;

    @BeforeAll
    static void startIsolatedWebApplication() {
        POSTGRES.start();
        context = startApplication(true);
        baseUri = baseUri(context);
    }

    private static ConfigurableApplicationContext startApplication(boolean local) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SalaryManagementApplication.class);
        if (local) {
            builder.profiles("local");
        }
        return builder.run("--server.port=0", "--server.address=127.0.0.1", "--debug=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--app.security.hr.username=" + USERNAME,
                "--app.security.hr.password-hash=" + PASSWORD_HASH);
    }

    @AfterAll
    static void closeApplicationAndDatabase() {
        if (context != null) {
            context.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void startAnonymousClient() {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void publicCsrfEndpointIssuesReadableCookieWithoutCreatingAuthenticatedSession() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/csrf");
        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
        String cookie = setCookie(response, "XSRF-TOKEN");
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertFalse(cookie.toLowerCase().contains("httponly"));
        assertFalse(cookie.toLowerCase().contains("secure"));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertFalse(cookies.getCookieStore().getCookies().stream().anyMatch(c -> c.getName().equals("JSESSIONID")));
        assertProblem(get("/api/v1/auth/session"), 401, "AUTHENTICATION_REQUIRED", "/api/v1/auth/session");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/employees", "/api/v1/reference-data", "/api/v1/analytics/compensation", "/api/v1/auth/session"})
    void protectsApiReadsWithJsonErrorsEvenWhenBrowserRequestsHtml(String path) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve(path + "?q=private-search"))
                .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertProblem(response, 401, "AUTHENTICATION_REQUIRED", path);
        assertFalse(response.body().contains("private-search"));
        assertTrue(response.headers().firstValue("Location").isEmpty());
    }

    @Test
    void logsInWithCookieAndHeaderAndKeepsAuthenticationAcrossRequests() throws Exception {
        String token = csrf();
        HttpResponse<String> login = login(USERNAME, PASSWORD, token);
        assertEquals(204, login.statusCode());
        assertEquals("", login.body());
        assertTrue(login.headers().firstValue("Location").isEmpty());
        String sessionCookie = setCookie(login, "JSESSIONID");
        assertTrue(sessionCookie.contains("HttpOnly"));
        assertTrue(sessionCookie.contains("SameSite=Lax"));
        assertFalse(sessionCookie.contains("Secure"));
        HttpResponse<String> session = get("/api/v1/auth/session");
        assertEquals(200, session.statusCode());
        assertEquals(USERNAME, JSON.readTree(session.body()).path("username").asString());
        assertEquals(1, JSON.readTree(session.body()).size());
        assertEquals(200, get("/api/v1/auth/session").statusCode());
        assertEquals(30, ((WebApplicationContext) context).getServletContext().getSessionTimeout());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "unknown-user"})
    void returnsGenericFailureForInvalidCredentials(String failure) throws Exception {
        String username = failure.equals("unknown-user") ? failure : USERNAME;
        String password = failure.equals("wrong-password") ? failure : PASSWORD;
        HttpResponse<String> response = login(username, password, csrf());
        assertProblem(response, 401, "INVALID_CREDENTIALS", "/api/v1/auth/login");
        assertFalse(response.body().contains(username));
        assertFalse(response.body().contains(password));
        assertProblem(get("/api/v1/auth/session"), 401, "AUTHENTICATION_REQUIRED", "/api/v1/auth/session");
    }

    @Test
    void loginRequiresCsrfHeaderEvenWhenCookieIsPresent() throws Exception {
        csrf();
        assertProblem(login(USERNAME, PASSWORD, null), 403, "CSRF_INVALID", "/api/v1/auth/login");
        assertProblem(login(USERNAME, PASSWORD, "invalid-token"), 403, "CSRF_INVALID", "/api/v1/auth/login");
    }

    @Test
    void loginRejectsUnsupportedContentTypeAfterCsrfValidation() throws Exception {
        String token = csrf();
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/login"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertProblem(response, 415, "UNSUPPORTED_MEDIA_TYPE", "/api/v1/auth/login");
    }

    @Test
    void csrfHeaderWithoutMatchingCookieIsRejected() throws Exception {
        String token = csrf();
        cookies.getCookieStore().removeAll();
        assertProblem(login(USERNAME, PASSWORD, token), 403, "CSRF_INVALID", "/api/v1/auth/login");
    }

    @Test
    void validCsrfTokenDoesNotAuthorizeAnAnonymousMutation() throws Exception {
        assertProblem(post("/api/v1/employees", "", csrf()), 401, "AUTHENTICATION_REQUIRED", "/api/v1/employees");
    }

    @Test
    void protectsLogoutAndMutationsAndInvalidatesTheOldSession() throws Exception {
        String beforeLogin = csrf();
        assertEquals(204, login(USERNAME, PASSWORD, beforeLogin).statusCode());
        String sessionId = cookieValue("JSESSIONID");
        String afterLogin = csrf();
        assertNotEquals(beforeLogin, afterLogin);
        assertProblem(post("/api/v1/auth/logout", "", beforeLogin), 403, "CSRF_INVALID", "/api/v1/auth/logout");
        assertProblem(post("/api/v1/auth/logout", "", null), 403, "CSRF_INVALID", "/api/v1/auth/logout");
        assertEquals(200, get("/api/v1/auth/session").statusCode());
        assertProblem(post("/api/v1/employees", "", null), 403, "CSRF_INVALID", "/api/v1/employees");

        HttpResponse<String> logout = post("/api/v1/auth/logout", "", afterLogin);
        assertEquals(204, logout.statusCode());
        assertEquals("", logout.body());
        assertProblem(get("/api/v1/auth/session"), 401, "AUTHENTICATION_REQUIRED", "/api/v1/auth/session");
        try (HttpClient replay = HttpClient.newHttpClient()) {
            HttpResponse<String> replayed = replay.send(HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/session"))
                    .header("Cookie", "JSESSIONID=" + sessionId).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertProblem(replayed, 401, "AUTHENTICATION_REQUIRED", "/api/v1/auth/session");
        }
        String afterLogout = csrf();
        assertNotEquals(afterLogin, afterLogout);
        assertProblem(login(USERNAME, PASSWORD, afterLogin), 403, "CSRF_INVALID", "/api/v1/auth/login");
        assertEquals(204, login(USERNAME, PASSWORD, afterLogout).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/api/v1/auth/login"})
    void doesNotCreateHtmlLoginPage(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertProblem(response, 401, "AUTHENTICATION_REQUIRED", path);
        assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty());
    }

    @Test
    void basicCredentialsCannotReplaceSessionLogin() throws Exception {
        String credentials = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(baseUri.resolve("/api/v1/auth/session"))
                .header("Authorization", "Basic " + credentials).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertProblem(response, 401, "AUTHENTICATION_REQUIRED", "/api/v1/auth/session");
        assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty());
    }

    @Test
    void getRequestCannotLogOutAnAuthenticatedUser() throws Exception {
        assertEquals(204, login(USERNAME, PASSWORD, csrf()).statusCode());
        assertEquals(404, get("/api/v1/auth/logout").statusCode());
        assertEquals(200, get("/api/v1/auth/session").statusCode());
    }

    @Test
    void defaultConfigurationMarksBothCookiesSecure() throws Exception {
        try (ConfigurableApplicationContext secureContext = startApplication(false);
                HttpClient secureClient = HttpClient.newHttpClient()) {
            URI secureBase = baseUri(secureContext);
            HttpResponse<String> csrf = secureClient.send(HttpRequest.newBuilder(secureBase.resolve("/api/v1/auth/csrf"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            String csrfCookie = setCookie(csrf, "XSRF-TOKEN");
            assertTrue(csrfCookie.contains("Secure"));
            String token = HttpCookie.parse(csrfCookie).getFirst().getValue();
            // Supply the cookie explicitly to inspect server flags over the test's HTTP transport.
            HttpResponse<String> login = secureClient.send(HttpRequest.newBuilder(secureBase.resolve("/api/v1/auth/login"))
                    .header("Cookie", "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(USERNAME, PASSWORD))).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(204, login.statusCode());
            String sessionCookie = setCookie(login, "JSESSIONID");
            assertTrue(sessionCookie.contains("Secure"));
            assertTrue(sessionCookie.contains("HttpOnly"));
            assertTrue(sessionCookie.contains("SameSite=Lax"));
        }
    }

    private String csrf() throws Exception {
        assertEquals(204, get("/api/v1/auth/csrf").statusCode());
        return cookieValue("XSRF-TOKEN");
    }

    private String cookieValue(String name) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(name))
                .findFirst().orElseThrow().getValue();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String username, String password, String token) throws Exception {
        return post("/api/v1/auth/login", form(username, password), token);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (token != null) {
            request.header("X-XSRF-TOKEN", token);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String form(String username, String password) {
        return "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }

    private static URI baseUri(ConfigurableApplicationContext application) {
        int port = ((WebServerApplicationContext) application).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port);
    }

    private static String setCookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith(name + "="))
                .reduce((first, last) -> last).orElseThrow();
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code, String path) {
        assertEquals(status, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        JsonNode body = JSON.readTree(response.body());
        assertEquals(status, body.path("status").asInt());
        assertEquals(code, body.path("code").asString());
        assertEquals(path, body.path("instance").asString());
        assertTrue(body.path("type").asString().startsWith("urn:salary-management:problem:"));
        assertFalse(body.path("title").asString().isBlank());
        assertFalse(body.path("detail").asString().isBlank());
        assertFalse(body.has("trace"));
        assertFalse(body.has("exception"));
    }
}
