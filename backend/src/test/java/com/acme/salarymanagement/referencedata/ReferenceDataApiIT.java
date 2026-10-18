package com.acme.salarymanagement.referencedata;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceDataApiIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static URI endpoint;
    private CookieManager cookies;
    private HttpClient client;

    @BeforeAll
    static void startIsolatedApplication() {
        POSTGRES.start();
        context = new SpringApplicationBuilder(SalaryManagementApplication.class).profiles("local")
                .run("--server.port=0", "--server.address=127.0.0.1", "--debug=false",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--app.security.hr.username=test-hr",
                        "--app.security.hr.password-hash=" + new BCryptPasswordEncoder(10).encode("synthetic-test-password"));
        jdbc = context.getBean(JdbcTemplate.class);
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/reference-data");
    }

    @AfterAll
    static void closeApplicationAndDatabase() {
        if (context != null) {
            context.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void restoreCatalogsAndAuthenticateInIsolatedDatabase() throws Exception {
        jdbc.execute("TRUNCATE employees, departments, countries, currencies RESTART IDENTITY");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__insert_reference_data.sql"))
                .execute(context.getBean(javax.sql.DataSource.class));
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        assertEquals(204, get(endpoint.resolve("/api/v1/auth/csrf")).statusCode());
        HttpRequest login = HttpRequest.newBuilder(endpoint.resolve("/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-XSRF-TOKEN", csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("username=test-hr&password=synthetic-test-password")).build();
        assertEquals(204, client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void returnsCompleteSupportedCatalogsWithExactTypesAndStableOrderWithoutWrites() throws Exception {
        var before = catalogRows();
        HttpResponse<String> response = get(endpoint);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode expected = JSON.readTree("""
                {
                  "departments": [
                    {"id":"7","name":"Customer Support"}, {"id":"1","name":"Engineering"},
                    {"id":"2","name":"Finance"}, {"id":"3","name":"HR"},
                    {"id":"8","name":"Legal"}, {"id":"5","name":"Marketing"},
                    {"id":"6","name":"Operations"}, {"id":"4","name":"Sales"}
                  ],
                  "countries": [
                    {"code":"DE","name":"Germany"}, {"code":"GB","name":"United Kingdom"},
                    {"code":"IN","name":"India"}, {"code":"JP","name":"Japan"},
                    {"code":"KW","name":"Kuwait"}, {"code":"US","name":"United States"}
                  ],
                  "currencies": [
                    {"code":"EUR","name":"Euro","minorUnits":2},
                    {"code":"GBP","name":"Pound Sterling","minorUnits":2},
                    {"code":"INR","name":"Indian Rupee","minorUnits":2},
                    {"code":"JPY","name":"Yen","minorUnits":0},
                    {"code":"KWD","name":"Kuwaiti Dinar","minorUnits":3},
                    {"code":"USD","name":"US Dollar","minorUnits":2}
                  ]
                }
                """);
        assertEquals(expected, JSON.readTree(response.body()));
        assertEquals(expected, JSON.readTree(get(endpoint).body()));
        assertEquals(before, catalogRows());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM employees", Long.class));
    }

    @Test
    void readsCurrentDatabaseValuesAndPreservesIdsBeyondJavaScriptIntegerPrecision() throws Exception {
        assertEquals(200, get(endpoint).statusCode());
        jdbc.update("INSERT INTO departments (id, name) OVERRIDING SYSTEM VALUE VALUES (?, ?)",
                9007199254740993L, "Accounting 李");
        jdbc.update("INSERT INTO countries (code, name) VALUES ('AU', 'Australia')");
        jdbc.update("INSERT INTO currencies (code, name, minor_units) VALUES ('AUD', 'Australian Dollar', 2)");
        jdbc.update("UPDATE currencies SET minor_units = 4 WHERE code = 'USD'");
        HttpResponse<String> response = get(endpoint);
        assertEquals(200, response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(JSON.readTree("""
                {"id":"9007199254740993","name":"Accounting 李"}
                """), body.path("departments").get(0));
        assertEquals("AU", body.path("countries").get(0).path("code").asString());
        assertEquals("AUD", body.path("currencies").get(0).path("code").asString());
        JsonNode usd = body.path("currencies").valueStream()
                .filter(currency -> currency.path("code").asString().equals("USD")).findFirst().orElseThrow();
        assertTrue(usd.path("minorUnits").isIntegralNumber());
        assertEquals(4, usd.path("minorUnits").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"departments", "countries", "currencies", "all"})
    void returnsEmptyArraysForEmptyCatalogs(String catalog) throws Exception {
        // The table names come only from this fixed test allowlist.
        for (String table : List.of("departments", "countries", "currencies")) {
            if (catalog.equals("all") || catalog.equals(table)) {
                jdbc.execute("DELETE FROM " + table);
            }
        }
        HttpResponse<String> response = get(endpoint);
        assertEquals(200, response.statusCode());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(3, body.size());
        for (String table : List.of("departments", "countries", "currencies")) {
            assertTrue(body.path(table).isArray());
            int expectedSize = table.equals("departments") ? 8 : 6;
            if (catalog.equals("all") || catalog.equals(table)) {
                expectedSize = 0;
            }
            assertEquals(expectedSize, body.path(table).size());
        }
    }

    @Test
    void requiresAuthenticationWithoutRedirectingOrReturningCatalogs() throws Exception {
        cookies.getCookieStore().removeAll();
        HttpResponse<String> response = get(endpoint);
        assertProblem(response, 401, "AUTHENTICATION_REQUIRED");
        assertFalse(response.headers().firstValue("Location").isPresent());
        assertFalse(JSON.readTree(response.body()).has("departments"));
    }

    @Test
    void authenticatedGetNeedsNeitherCsrfCookieNorHeader() throws Exception {
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .toList().forEach(cookie -> cookies.getCookieStore().remove(endpoint, cookie));
        assertEquals(200, get(endpoint).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void offersNoCatalogWriteEndpoints(String method) throws Exception {
        var before = catalogRows();
        assertEquals(204, get(endpoint.resolve("/api/v1/auth/csrf")).statusCode());
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrfToken())
                .method(method, HttpRequest.BodyPublishers.ofString("{}")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertProblem(response, 405, "METHOD_NOT_ALLOWED");
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
        assertEquals(before, catalogRows());
    }

    @Test
    void rejectsUnsupportedResponseMediaType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html").GET().build();
        assertProblem(client.send(request, HttpResponse.BodyHandlers.ofString()), 406, "NOT_ACCEPTABLE");
    }

    private List<?> catalogRows() {
        return List.of(jdbc.queryForList("SELECT * FROM departments ORDER BY id"),
                jdbc.queryForList("SELECT * FROM countries ORDER BY code"),
                jdbc.queryForList("SELECT * FROM currencies ORDER BY code"));
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String csrfToken() {
        return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        JsonNode body = JSON.readTree(response.body());
        assertEquals(status, body.path("status").asInt());
        assertEquals(code, body.path("code").asString());
        assertEquals(endpoint.getPath(), body.path("instance").asString());
    }
}
