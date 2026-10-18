package com.acme.salarymanagement.analytics;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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

class CompensationAnalyticsApiIT {

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
        endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/analytics/compensation");
    }

    @AfterAll
    static void closeApplicationAndDatabase() {
        if (context != null) {
            context.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void restoreOnlyIsolatedDatabaseAndAuthenticate() throws Exception {
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

    @ParameterizedTest
    @ValueSource(strings = {"department", "country"})
    void returnsExactCurrencySeparatedJsonForBothGroupingsWithoutWrites(String grouping) throws Exception {
        insertFixture();
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        HttpResponse<String> response = get(query("groupBy=" + grouping));
        JsonNode body = assertAnalytics(response, grouping, 8);
        assertEquals(JSON.readTree("""
                [
                  {"currencyCode":"JPY","headcount":2,"totalSalary":"2001","averageSalary":"1000","minSalary":"1000","maxSalary":"1001"},
                  {"currencyCode":"KWD","headcount":2,"totalSalary":"2.469","averageSalary":"1.234","minSalary":"1.234","maxSalary":"1.235"},
                  {"currencyCode":"USD","headcount":4,"totalSalary":"130.00","averageSalary":"32.50","minSalary":"10.00","maxSalary":"100.00"}
                ]
                """), body.path("currencySummaries"));
        String expectedGroups = grouping.equals("department") ? """
                [
                  {"groupKey":"1","groupLabel":"Engineering","currencyCode":"JPY","headcount":1,"totalSalary":"1000","averageSalary":"1000","minSalary":"1000","maxSalary":"1000"},
                  {"groupKey":"1","groupLabel":"Engineering","currencyCode":"KWD","headcount":1,"totalSalary":"1.234","averageSalary":"1.234","minSalary":"1.234","maxSalary":"1.234"},
                  {"groupKey":"1","groupLabel":"Engineering","currencyCode":"USD","headcount":1,"totalSalary":"100.00","averageSalary":"100.00","minSalary":"100.00","maxSalary":"100.00"},
                  {"groupKey":"2","groupLabel":"Finance","currencyCode":"JPY","headcount":1,"totalSalary":"1001","averageSalary":"1001","minSalary":"1001","maxSalary":"1001"},
                  {"groupKey":"2","groupLabel":"Finance","currencyCode":"KWD","headcount":1,"totalSalary":"1.235","averageSalary":"1.235","minSalary":"1.235","maxSalary":"1.235"},
                  {"groupKey":"2","groupLabel":"Finance","currencyCode":"USD","headcount":3,"totalSalary":"30.00","averageSalary":"10.00","minSalary":"10.00","maxSalary":"10.00"}
                ]
                """ : """
                [
                  {"groupKey":"IN","groupLabel":"India","currencyCode":"JPY","headcount":1,"totalSalary":"1000","averageSalary":"1000","minSalary":"1000","maxSalary":"1000"},
                  {"groupKey":"IN","groupLabel":"India","currencyCode":"KWD","headcount":1,"totalSalary":"1.235","averageSalary":"1.235","minSalary":"1.235","maxSalary":"1.235"},
                  {"groupKey":"IN","groupLabel":"India","currencyCode":"USD","headcount":2,"totalSalary":"110.00","averageSalary":"55.00","minSalary":"10.00","maxSalary":"100.00"},
                  {"groupKey":"JP","groupLabel":"Japan","currencyCode":"JPY","headcount":1,"totalSalary":"1001","averageSalary":"1001","minSalary":"1001","maxSalary":"1001"},
                  {"groupKey":"KW","groupLabel":"Kuwait","currencyCode":"KWD","headcount":1,"totalSalary":"1.234","averageSalary":"1.234","minSalary":"1.234","maxSalary":"1.234"},
                  {"groupKey":"US","groupLabel":"United States","currencyCode":"USD","headcount":2,"totalSalary":"20.00","averageSalary":"10.00","minSalary":"10.00","maxSalary":"10.00"}
                ]
                """;
        assertEquals(JSON.readTree(expectedGroups), body.path("groups"));
        if (grouping.equals("department")) {
            assertEquals(body, assertAnalytics(get(endpoint), "department", 8));
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @ParameterizedTest
    @CsvSource({"departmentId=0001,3", "countryCode=%20in%20,4", "currencyCode=%20usd%20,4",
            "q=ONE,1", "q=emp-1,1", "groupBy=country&departmentId=0001&countryCode=%20in%20&currencyCode=%20usd%20&q=%20oNe%20,1"})
    void bindsSearchAndEveryFilterAndNormalizesCodes(String parameters, long expected) throws Exception {
        insertFixture();
        JsonNode body = assertAnalytics(get(query(parameters)), parameters.contains("groupBy=country") ? "country" : "department", expected);
        if (parameters.contains("groupBy=country")) {
            assertEquals("IN", body.path("groups").get(0).path("groupKey").asString());
            assertEquals("100.00", body.path("currencySummaries").get(0).path("totalSalary").asString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "' OR 1=1 --", "Zoë 李"})
    void decodesLiteralAndUnicodeSearchWithoutInterpretingItAsSql(String term) throws Exception {
        insert("EMP-1", "Person " + term, 1, "IN", "USD", "10");
        insert("EMP-2", "Person Other", 1, "IN", "USD", "20");
        JsonNode body = assertAnalytics(get(query("q=" + encoded(term))), "department", 1);
        assertEquals("10.00", body.path("currencySummaries").get(0).path("totalSalary").asString());
    }

    @Test
    void blankSearchIsAbsentAndLengthCountsUnicodeCodePointsAfterTrimming() throws Exception {
        String name = "😀".repeat(200);
        insert("EMP-1", name, 1, "IN", "USD", "10");
        assertAnalytics(get(query("q=" + encoded(" \u2003 "))), "department", 1);
        assertAnalytics(get(query("q=" + encoded(" " + name + " "))), "department", 1);
        assertFieldError(get(query("q=" + encoded(name + "😀"))), "q", "TOO_LONG");
        assertFieldError(get(query("q=" + encoded("bad\0search"))), "q", "INVALID_FORMAT");
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM employees", Long.class));
    }

    @Test
    void emptyDatabaseAndSupportedNoMatchFiltersReturnEmptyCollections() throws Exception {
        assertEmpty(get(endpoint), "department");
        assertEmpty(get(query("groupBy=country")), "country");
        insertFixture();
        assertEmpty(get(query("groupBy=country&countryCode=DE")), "country");
        assertEmpty(get(query("q=no-matching-person")), "department");
    }

    @Test
    void serializesLargeIdsAndAggregateTotalsAsExactPlainStringsInNumericGroupOrder() throws Exception {
        jdbc.update("INSERT INTO departments (id, name) OVERRIDING SYSTEM VALUE VALUES (10, 'Ten'), (9007199254740993, 'Accounting 李')");
        insert("EMP-1", "Person One", 9007199254740993L, "IN", "USD", "999999999999999.99");
        insert("EMP-2", "Person Two", 9007199254740993L, "IN", "USD", "999999999999999.99");
        insert("EMP-3", "Person Three", 10, "IN", "USD", "1");
        JsonNode body = assertAnalytics(get(endpoint), "department", 3);
        assertEquals("10", body.path("groups").get(0).path("groupKey").asString());
        JsonNode large = body.path("groups").get(1);
        assertEquals("9007199254740993", large.path("groupKey").asString());
        assertEquals("Accounting 李", large.path("groupLabel").asString());
        assertEquals("1999999999999999.98", large.path("totalSalary").asString());
        assertEquals("2000000000000000.98", body.path("currencySummaries").get(0).path("totalSalary").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "DEPARTMENT", "Country", " department", "department ", "department,country", "salary", "null"})
    void rejectsInvalidGroupingWithAFieldError(String grouping) throws Exception {
        HttpResponse<String> response = get(query("groupBy=" + encoded(grouping)));
        assertFieldError(response, "groupBy", "INVALID_FORMAT");
        assertFalse(JSON.readTree(response.body()).has("groups"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupBy", "q", "departmentId", "countryCode", "currencyCode"})
    void rejectsRepeatedParametersIncludingIdenticalValues(String field) throws Exception {
        String value = switch (field) {
            case "groupBy" -> "department";
            case "departmentId" -> "1";
            case "countryCode" -> "IN";
            case "currencyCode" -> "USD";
            default -> "person";
        };
        assertFieldError(get(query(field + "=" + value + "&" + field + "=" + value)), field, "SINGLE_VALUE_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "size=25", "sort=employeeNumber,asc", "groupby=country", "private-query-value=hidden"})
    void rejectsUnsupportedParametersWithoutEchoingNamesOrValues(String parameters) throws Exception {
        HttpResponse<String> response = get(query(parameters));
        assertFieldError(response, "query", "UNSUPPORTED_PARAMETER");
        assertFalse(response.body().contains(parameters.split("=", 2)[0]));
        assertFalse(response.body().contains("hidden"));
    }

    @ParameterizedTest
    @CsvSource({"departmentId,0,MUST_BE_POSITIVE", "departmentId,-1,INVALID_FORMAT", "departmentId,1.0,INVALID_FORMAT",
            "departmentId,9223372036854775808,OUT_OF_RANGE", "departmentId,9223372036854775807,UNKNOWN_REFERENCE",
            "countryCode,ZZ,UNKNOWN_REFERENCE", "currencyCode,ZZZ,UNKNOWN_REFERENCE",
            "countryCode,USA,INVALID_FORMAT", "currencyCode,US,INVALID_FORMAT", "countryCode,ıN,INVALID_FORMAT"})
    void rejectsMalformedAndUnknownFiltersEvenForEmptyData(String field, String value, String code) throws Exception {
        assertFieldError(get(query(field + "=" + encoded(value))), field, code);
        assertFieldError(get(query("q=no-match&" + field + "=" + encoded(value))), field, code);
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM employees", Long.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departmentId", "countryCode", "currencyCode"})
    void rejectsSuppliedBlankFilters(String field) throws Exception {
        assertFieldError(get(query(field + "=")), field, "REQUIRED");
        assertFieldError(get(query(field + "=%20")), field, "REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "groupBy=bad", "departmentId=bad", "page=0", "groupBy=department&groupBy=country"})
    void requiresAuthenticationBeforeValidationAndReturnsNoSalaryData(String parameters) throws Exception {
        insertFixture();
        cookies.getCookieStore().removeAll();
        HttpResponse<String> response = get(query(parameters));
        assertProblem(response, 401, "AUTHENTICATION_REQUIRED");
        assertFalse(response.headers().firstValue("Location").isPresent());
        assertFalse(JSON.readTree(response.body()).has("groups"));
        assertFalse(JSON.readTree(response.body()).has("currencySummaries"));
    }

    @Test
    void authenticatedReadRequiresNeitherCsrfCookieNorHeader() throws Exception {
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .toList().forEach(cookie -> cookies.getCookieStore().remove(endpoint, cookie));
        assertEmpty(get(endpoint), "department");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void offersNoAnalyticsWriteEndpoint(String method) throws Exception {
        insertFixture();
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        assertEquals(204, get(endpoint.resolve("/api/v1/auth/csrf")).statusCode());
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", csrfToken())
                .method(method, HttpRequest.BodyPublishers.ofString("{}")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertProblem(response, 405, "METHOD_NOT_ALLOWED");
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @Test
    void rejectsUnsupportedResponseMediaType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html").GET().build();
        assertProblem(client.send(request, HttpResponse.BodyHandlers.ofString()), 406, "NOT_ACCEPTABLE");
    }

    private JsonNode assertAnalytics(HttpResponse<String> response, String grouping, long count) {
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode body = JSON.readTree(response.body());
        assertEquals(4, body.size());
        assertEquals(grouping, body.path("groupBy").asString());
        assertTrue(body.path("totalHeadcount").isIntegralNumber());
        assertEquals(count, body.path("totalHeadcount").asLong());
        for (String collection : List.of("currencySummaries", "groups")) {
            assertTrue(body.path(collection).isArray());
            for (JsonNode row : body.path(collection)) {
                assertEquals(collection.equals("groups") ? 8 : 6, row.size());
                assertTrue(row.path("headcount").isIntegralNumber());
                for (String field : List.of("totalSalary", "averageSalary", "minSalary", "maxSalary")) {
                    assertTrue(row.path(field).isString());
                    assertTrue(row.path(field).asString().matches("[0-9]+(?:\\.[0-9]+)?"));
                }
                if (collection.equals("groups")) {
                    assertTrue(row.path("groupKey").isString());
                    assertTrue(row.path("groupLabel").isString());
                }
            }
        }
        return body;
    }

    private void assertEmpty(HttpResponse<String> response, String grouping) {
        JsonNode body = assertAnalytics(response, grouping, 0);
        assertEquals(0, body.path("groups").size());
        assertEquals(0, body.path("currencySummaries").size());
    }

    private void assertFieldError(HttpResponse<String> response, String field, String code) {
        JsonNode error = assertProblem(response, 400, "VALIDATION_ERROR").path("errors").get(0);
        assertEquals(field, error.path("field").asString());
        assertEquals(code, error.path("code").asString());
    }

    private JsonNode assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        JsonNode body = JSON.readTree(response.body());
        assertEquals(status, body.path("status").asInt());
        assertEquals(code, body.path("code").asString());
        assertEquals(endpoint.getPath(), body.path("instance").asString());
        return body;
    }

    private void insertFixture() {
        insert("EMP-1", "Person One", 1, "IN", "USD", "100");
        insert("EMP-2", "Person Two", 2, "US", "USD", "10");
        insert("EMP-3", "Person Three", 2, "IN", "USD", "10");
        insert("EMP-4", "Person Four", 2, "US", "USD", "10");
        insert("EMP-5", "Person Five", 1, "IN", "JPY", "1000");
        insert("EMP-6", "Person Six", 2, "JP", "JPY", "1001");
        insert("EMP-7", "Person Seven", 1, "KW", "KWD", "1.234");
        insert("EMP-8", "Person Eight", 2, "IN", "KWD", "1.235");
    }

    private void insert(String number, String name, long department, String country, String currency, String salary) {
        jdbc.update("""
                INSERT INTO employees (employee_number, full_name, department_id, country_code, currency_code, salary_amount)
                VALUES (?, ?, ?, ?, ?, ?::numeric)
                """, number, name, department, country, currency, salary);
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI query(String parameters) {
        return URI.create(endpoint + (parameters.isEmpty() ? "" : "?" + parameters));
    }

    private String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String csrfToken() {
        return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
    }
}
