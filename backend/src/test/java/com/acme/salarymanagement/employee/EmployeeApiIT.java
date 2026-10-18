package com.acme.salarymanagement.employee;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.acme.salarymanagement.SalaryManagementApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeApiIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static URI endpoint;
    private CookieManager cookies;
    private HttpClient client;
    private String token;
    private long departmentId;

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
        endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/employees");
    }

    @AfterAll
    static void closeApplicationAndDatabase() {
        if (context != null) {
            context.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void authenticateAndClearOnlyTestDatabase() throws Exception {
        jdbc.update("DELETE FROM employees");
        departmentId = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Engineering'", Long.class);
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        refreshCsrf();
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(endpoint.resolve("/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString("username=test-hr&password=synthetic-test-password")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, login.statusCode());
        refreshCsrf();
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void createsEmployeeWithExactStringContractAndCommittedValues() throws Exception {
        ObjectNode request = validRequest().put("employeeNumber", " emp-1 ").put("fullName", "  Zoë  李  ")
                .put("countryCode", " in ").put("currencyCode", " usd ").put("salaryAmount", "125000.1200");
        HttpResponse<String> response = post(request.toString());
        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode body = JSON.readTree(response.body());
        for (String field : List.of("id", "departmentId", "version", "salaryAmount")) {
            assertTrue(body.path(field).isString(), field + " must be a JSON string");
        }
        assertEquals(12, body.size());
        long id = Long.parseLong(body.path("id").asString());
        assertTrue(id > 0);
        assertEquals("/api/v1/employees/" + id, response.headers().firstValue("Location").orElseThrow());
        assertEquals("EMP-1", body.path("employeeNumber").asString());
        assertEquals("Zoë  李", body.path("fullName").asString());
        assertEquals(Long.toString(departmentId), body.path("departmentId").asString());
        assertEquals("Engineering", body.path("departmentName").asString());
        assertEquals("IN", body.path("countryCode").asString());
        assertEquals("India", body.path("countryName").asString());
        assertEquals("USD", body.path("currencyCode").asString());
        assertEquals("125000.12", body.path("salaryAmount").asString());
        assertEquals("0", body.path("version").asString());
        assertTrue(body.path("createdAt").asString().endsWith("Z"));
        assertEquals(Instant.parse(body.path("createdAt").asString()), Instant.parse(body.path("updatedAt").asString()));
        assertEquals(1L, countEmployees());
        assertEquals("Zoë  李", jdbc.queryForObject("SELECT full_name FROM employees WHERE id = ?", String.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT salary_amount FROM employees WHERE id = ?", java.math.BigDecimal.class, id)
                .compareTo(new java.math.BigDecimal("125000.12")));
    }

    @ParameterizedTest
    @CsvSource({"JPY,1200.0000,1200", "KWD,12.3450,12.345", "USD,999999999999999.99,999999999999999.99"})
    void formatsExactSalaryAtCurrencyMinorUnits(String currency, String input, String expected) throws Exception {
        HttpResponse<String> response = post(validRequest().put("currencyCode", currency).put("salaryAmount", input).toString());
        assertEquals(201, response.statusCode());
        assertEquals(expected, JSON.readTree(response.body()).path("salaryAmount").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"employeeNumber", "fullName", "departmentId", "countryCode", "salaryAmount", "currencyCode"})
    void rejectsMissingNullAndBlankRequiredFields(String field) throws Exception {
        ObjectNode request = validRequest();
        request.remove(field);
        assertFieldError(post(request.toString()), field, "REQUIRED");
        assertFieldError(post(validRequest().putNull(field).toString()), field, "REQUIRED");
        assertFieldError(post(validRequest().put(field, "   ").toString()), field, "REQUIRED");
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "version", "createdAt", "updatedAt", "departmentName", "unexpected"})
    void rejectsUnknownAndServerManagedFields(String field) throws Exception {
        assertProblem(post(validRequest().put(field, "client-controlled").toString()), 400, "INVALID_REQUEST");
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"employeeNumber,123", "fullName,true", "departmentId,1", "salaryAmount,125000.12", "countryCode,[]", "currencyCode,{}"})
    void rejectsNonStringJsonValuesWithoutCoercion(String field, String value) throws Exception {
        ObjectNode request = validRequest();
        request.set(field, JSON.readTree(value));
        assertProblem(post(request.toString()), 400, "INVALID_REQUEST");
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "1.0", "1e0", "+1", " 1", "9223372036854775808"})
    void rejectsInvalidOrOutOfRangeDepartmentIds(String id) throws Exception {
        HttpResponse<String> response = post(validRequest().put("departmentId", id).toString());
        JsonNode body = assertProblem(response, 400, "VALIDATION_ERROR");
        assertEquals("departmentId", body.path("errors").get(0).path("field").asString());
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"departmentId,9223372036854775807", "countryCode,ZZ", "currencyCode,ZZZ"})
    void reportsUnknownReferencesAsFieldErrors(String field, String value) throws Exception {
        assertFieldError(post(validRequest().put(field, value).toString()), field, "UNKNOWN_REFERENCE");
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"USD,1.001,EXCESS_PRECISION", "JPY,1.1,EXCESS_PRECISION", "KWD,1.2345,EXCESS_PRECISION",
            "USD,0,MUST_BE_POSITIVE", "USD,1e3,INVALID_FORMAT", "USD,1000000000000000,OUT_OF_RANGE"})
    void reportsMonetaryValidationWithoutWritingOrEchoingSalary(String currency, String amount, String code) throws Exception {
        HttpResponse<String> response = post(validRequest().put("currencyCode", currency).put("salaryAmount", amount).toString());
        assertFieldError(response, "salaryAmount", code);
        assertFalse(response.body().contains("\"rejectedValue\""));
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{broken", "{} {}"})
    void rejectsMissingMalformedAndWrongShapeBodies(String body) throws Exception {
        assertProblem(post(body), 400, "INVALID_REQUEST");
        assertEquals(0L, countEmployees());
    }

    @Test
    void reportsAllMissingFieldsInStableOrder() throws Exception {
        JsonNode errors = assertProblem(post("{}"), 400, "VALIDATION_ERROR").path("errors");
        assertEquals(List.of("countryCode", "currencyCode", "departmentId", "employeeNumber", "fullName", "salaryAmount"),
                errors.valueStream().map(error -> error.path("field").asString()).toList());
    }

    @Test
    void preservesUnicodeCharacterLimitAfterTrimming() throws Exception {
        String name = "𠀀".repeat(200);
        assertEquals(201, post(validRequest().put("fullName", " " + name + " ").toString()).statusCode());
        assertFieldError(post(validRequest().put("employeeNumber", "EMP-2").put("fullName", name + "李").toString()), "fullName", "TOO_LONG");
        assertEquals(1L, countEmployees());
    }

    @Test
    void reportsNormalizedDuplicateAsConflictWithoutLeakingDatabaseDetails() throws Exception {
        assertEquals(201, post(validRequest().toString()).statusCode());
        HttpResponse<String> response = post(validRequest().put("employeeNumber", " emp-1 ").toString());
        assertProblem(response, 409, "EMPLOYEE_NUMBER_CONFLICT");
        assertFalse(response.body().contains("EMP-1"));
        assertFalse(response.body().contains("uq_employees"));
        assertFalse(response.body().contains("23505"));
        assertEquals(1L, countEmployees());
    }

    @Test
    void concurrentDuplicateRequestsProduceOneCreatedResponseAndOneConflict() throws Exception {
        String request = validRequest().toString();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> create = () -> {
                start.await(10, TimeUnit.SECONDS);
                return post(request);
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            assertProblem(responses.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow(),
                    409, "EMPLOYEE_NUMBER_CONFLICT");
        }
        assertEquals(1L, countEmployees());
    }

    @Test
    void requiresBothAuthenticationAndCsrfBeforeWriting() throws Exception {
        assertProblem(send(validRequest().toString(), "application/json", null), 403, "CSRF_INVALID");
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .toList().forEach(cookie -> cookies.getCookieStore().remove(endpoint, cookie));
        assertProblem(post(validRequest().toString()), 401, "AUTHENTICATION_REQUIRED");
        assertEquals(0L, countEmployees());
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        assertProblem(send("employeeNumber=EMP-1", "application/x-www-form-urlencoded", token), 415, "UNSUPPORTED_MEDIA_TYPE");
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"USD,125000.1200", "JPY,1200.0000", "KWD,12.3450"})
    void followsCreatedLocationWithoutChangingEmployeeData(String currency, String amount) throws Exception {
        HttpResponse<String> created = post(validRequest().put("fullName", "  Zoë  李  ")
                .put("currencyCode", currency).put("salaryAmount", amount).toString());
        assertEquals(201, created.statusCode());
        String location = created.headers().firstValue("Location").orElseThrow();
        long id = Long.parseLong(JSON.readTree(created.body()).path("id").asString());
        var before = jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", id);
        // Reads need the authenticated session, but no CSRF cookie/header.
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .toList().forEach(cookie -> cookies.getCookieStore().remove(endpoint, cookie));
        HttpResponse<String> fetched = get(endpoint.resolve(location));
        assertEquals(200, fetched.statusCode());
        assertTrue(fetched.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(fetched.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(JSON.readTree(created.body()), JSON.readTree(fetched.body()));
        assertEquals(200, get(endpoint.resolve(location)).statusCode());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", id));
        assertEquals(1L, countEmployees());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "1.0", "1e0", "abc", "9223372036854775808", "%201", "1%20"})
    void rejectsInvalidEmployeePathIds(String id) throws Exception {
        URI uri = URI.create(endpoint + "/" + id);
        JsonNode problem = assertProblem(get(uri), 400, "VALIDATION_ERROR", uri.getRawPath());
        assertEquals("id", problem.path("errors").get(0).path("field").asString());
        assertEquals(0L, countEmployees());
    }

    @Test
    void reportsMissingEmployeeSeparatelyFromUnmappedResources() throws Exception {
        URI uri = URI.create(endpoint + "/9223372036854775807");
        HttpResponse<String> response = get(URI.create(uri + "?q=private-search"));
        assertProblem(response, 404, "EMPLOYEE_NOT_FOUND", uri.getPath());
        assertFalse(response.body().contains("EmployeeNotFoundException"));
        assertFalse(response.body().contains("private-search"));
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "abc", "9223372036854775807"})
    void requiresAuthenticationBeforeLookupOrIdValidation(String id) throws Exception {
        cookies.getCookieStore().removeAll();
        URI uri = URI.create(endpoint + "/" + id);
        assertProblem(get(uri), 401, "AUTHENTICATION_REQUIRED", uri.getPath());
        assertEquals(0L, countEmployees());
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private ObjectNode validRequest() {
        return JSON.createObjectNode().put("employeeNumber", "EMP-1").put("fullName", "Asha Rao")
                .put("departmentId", Long.toString(departmentId)).put("countryCode", "IN")
                .put("salaryAmount", "1000.00").put("currencyCode", "USD");
    }

    private HttpResponse<String> post(String body) throws Exception {
        return send(body, "application/json", token);
    }

    private HttpResponse<String> send(String body, String contentType, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).header("Content-Type", contentType);
        if (csrf != null) {
            request.header("X-XSRF-TOKEN", csrf);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void refreshCsrf() throws Exception {
        assertEquals(204, client.send(HttpRequest.newBuilder(endpoint.resolve("/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        token = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
    }

    private long countEmployees() {
        return jdbc.queryForObject("SELECT count(*) FROM employees", Long.class);
    }

    private JsonNode assertProblem(HttpResponse<String> response, int status, String code) {
        return assertProblem(response, status, code, "/api/v1/employees");
    }

    private JsonNode assertProblem(HttpResponse<String> response, int status, String code, String path) {
        assertEquals(status, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        JsonNode body = JSON.readTree(response.body());
        assertEquals(status, body.path("status").asInt());
        assertEquals(code, body.path("code").asString());
        assertEquals(path, body.path("instance").asString());
        assertFalse(body.has("trace"));
        assertFalse(body.has("exception"));
        return body;
    }

    private void assertFieldError(HttpResponse<String> response, String field, String code) {
        JsonNode errors = assertProblem(response, 400, "VALIDATION_ERROR").path("errors");
        assertEquals(field, errors.get(0).path("field").asString());
        assertEquals(code, errors.get(0).path("code").asString());
        assertFalse(errors.get(0).has("rejectedValue"));
    }
}
