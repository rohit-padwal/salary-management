package com.acme.salarymanagement.employee;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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

    @Test
    void listsDefaultPageWithExactEnvelopeAndExistingEmployeeRepresentations() throws Exception {
        insertListingEmployees(27);
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        HttpResponse<String> response = get(endpoint);
        JsonNode page = assertPage(response, 0, 25, 27, 2);
        assertEquals(25, page.path("items").size());
        assertEquals(employeeNumbers(1, 26), page.path("items").valueStream()
                .map(item -> item.path("employeeNumber").asString()).toList());
        JsonNode first = page.path("items").get(0);
        assertEquals(JSON.readTree(get(URI.create(endpoint + "/" + first.path("id").asString())).body()), first);
        for (String field : List.of("id", "departmentId", "version", "salaryAmount")) {
            assertTrue(first.path(field).isString());
        }
        assertEquals("12.345", first.path("salaryAmount").asString());
        assertEquals("Engineering", first.path("departmentName").asString());
        assertEquals("India", first.path("countryName").asString());
        assertEquals(page, JSON.readTree(get(endpoint).body()));
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @Test
    void traversesPagesWithoutGapsOrDuplicatesAndPreservesRealTotalsPastTheEnd() throws Exception {
        insertListingEmployees(7);
        List<String> listed = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            JsonNode page = assertPage(get(URI.create(endpoint + "?page=" + index + "&size=3")), index, 3, 7, 3);
            assertEquals(index < 2 ? 3 : 1, page.path("items").size());
            page.path("items").valueStream().map(item -> item.path("employeeNumber").asString()).forEach(listed::add);
        }
        assertEquals(employeeNumbers(1, 8), listed);
        assertEquals(0, assertPage(get(URI.create(endpoint + "?page=3&size=3")), 3, 3, 7, 3).path("items").size());
    }

    @Test
    void returnsEmptyPageAndZeroTotalsForAnEmptyDatabase() throws Exception {
        assertEquals(0, assertPage(get(endpoint), 0, 25, 0, 0).path("items").size());
        assertEquals(0, assertPage(get(URI.create(endpoint + "?page=5&size=1")), 5, 1, 0, 0).path("items").size());
    }

    @Test
    void acceptsIndividualDefaultsLeadingZerosAndTheMaximumPageSize() throws Exception {
        insertListingEmployees(101);
        assertEquals(100, assertPage(get(URI.create(endpoint + "?size=100")), 0, 100, 101, 2).path("items").size());
        assertEquals(25, assertPage(get(URI.create(endpoint + "?page=1")), 1, 25, 101, 5).path("items").size());
        assertEquals(1, assertPage(get(URI.create(endpoint + "?page=000&size=001")), 0, 1, 101, 101).path("items").size());
    }

    @Test
    void acceptsALargeOutOfRangePageWithoutOverflowOrAnInvalidJpaOffset() throws Exception {
        insertListingEmployees(3);
        assertEquals(0, assertPage(get(URI.create(endpoint + "?page=2147483647&size=100")),
                Integer.MAX_VALUE, 100, 3, 1).path("items").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "%2B1", "1.0", "1e0", "%201", "1%20", "abc", "", "2147483648", "999999999999999999999", "%D9%A1"})
    void rejectsInvalidPageSyntaxOrRangeWithoutEchoingQueryValues(String value) throws Exception {
        JsonNode problem = assertProblem(get(URI.create(endpoint + "?page=" + value)), 400, "VALIDATION_ERROR");
        assertEquals("page", problem.path("errors").get(0).path("field").asString());
        assertFalse(problem.toString().contains("rejectedValue"));
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1", "%2B1", "1.0", "1e0", "%201", "1%20", "abc", "", "2147483648", "%EF%BC%91"})
    void rejectsInvalidPageSize(String value) throws Exception {
        JsonNode problem = assertProblem(get(URI.create(endpoint + "?size=" + value)), 400, "VALIDATION_ERROR");
        assertEquals("size", problem.path("errors").get(0).path("field").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0&page=1", "size=1&size=2", "page=0&page=0"})
    void rejectsRepeatedPaginationParameters(String query) throws Exception {
        assertProblem(get(URI.create(endpoint + "?" + query)), 400, "VALIDATION_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"departmentId=1", "countryCode=IN", "currencyCode=USD",
            "sort=employeeNumber,desc", "unexpected=private-value", "Page=1"})
    void rejectsUnsupportedParametersInsteadOfSilentlyIgnoringThem(String query) throws Exception {
        HttpResponse<String> response = get(URI.create(endpoint + "?" + query));
        JsonNode problem = assertProblem(response, 400, "VALIDATION_ERROR");
        assertEquals("query", problem.path("errors").get(0).path("field").asString());
        assertFalse(response.body().contains("private-search"));
        assertFalse(response.body().contains("private-value"));
    }

    @Test
    void listingRequiresAuthenticationBeforeValidatingQueryParameters() throws Exception {
        cookies.getCookieStore().removeAll();
        assertProblem(get(endpoint), 401, "AUTHENTICATION_REQUIRED");
        assertProblem(get(URI.create(endpoint + "?page=-1")), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void listingNeedsNoCsrfCookieOrHeader() throws Exception {
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .toList().forEach(cookie -> cookies.getCookieStore().remove(endpoint, cookie));
        assertPage(get(endpoint), 0, 25, 0, 0);
        assertPage(get(searchUri("name")), 0, 25, 0, 0);
    }

    @Test
    void searchesEmployeeNumberOrFullNameCaseInsensitivelyWithoutDuplicatesOrWrites() throws Exception {
        insertSearchEmployee("EMP-ALPHA", "Asha Rao");
        insertSearchEmployee("EMP-2", "Alpha Johnson");
        insertSearchEmployee("EMP-ALPHA-3", "Alpha Both");
        insertSearchEmployee("EMP-4", "Other Name");
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        assertSearchMatches("  AlPhA \t", "EMP-2", "EMP-ALPHA", "EMP-ALPHA-3");
        assertSearchMatches("lPh", "EMP-2", "EMP-ALPHA", "EMP-ALPHA-3");
        assertSearchMatches("emp-alp", "EMP-ALPHA", "EMP-ALPHA-3");
        assertSearchMatches("sha ra", "EMP-ALPHA");
        JsonNode first = assertPage(get(searchUri("sha ra")), 0, 25, 1, 1).path("items").get(0);
        assertEquals(JSON.readTree(get(URI.create(endpoint + "/" + first.path("id").asString())).body()), first);
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "'", "%_!\\", "' OR 1=1 --"})
    void searchesWildcardsEscapeCharactersAndQuotesAsLiteralData(String term) throws Exception {
        insertSearchEmployee("EMP-1", "Rate " + term + " Literal");
        insertSearchEmployee("EMP-2", "Rate Anything Literal");
        insertSearchEmployee("EMP-3", "Other Name");
        assertSearchMatches(term, "EMP-1");
        assertEquals(3L, countEmployees());
    }

    @Test
    void underscoresInEmployeeNumbersAreLiteralAndNamesSupportUnicodeCaseMatching() throws Exception {
        insertSearchEmployee("EMP_A", "Zoë 李");
        insertSearchEmployee("EMPXA", "Other Name");
        assertSearchMatches("_a", "EMP_A");
        assertSearchMatches("ZOË", "EMP_A");
        assertSearchMatches("李", "EMP_A");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void blankSearchBehavesLikeAnAbsentSearch(String term) throws Exception {
        insertListingEmployees(3);
        assertEquals(JSON.readTree(get(endpoint).body()), JSON.readTree(get(searchUri(term)).body()));
    }

    @Test
    void searchLengthUsesUnicodeCodePointsAfterTrimming() throws Exception {
        String name = "𠀀".repeat(200);
        insertSearchEmployee("EMP-1", name);
        assertSearchMatches(" " + name + " ", "EMP-1");
        HttpResponse<String> response = get(searchUri(name + "李"));
        JsonNode problem = assertProblem(response, 400, "VALIDATION_ERROR");
        assertEquals("q", problem.path("errors").get(0).path("field").asString());
        assertEquals("TOO_LONG", problem.path("errors").get(0).path("code").asString());
        assertFalse(response.body().contains(name));
    }

    @Test
    void searchPaginatesAndCountsOnlyMatchesIncludingPagesPastTheEnd() throws Exception {
        for (int number = 7; number > 0; number--) {
            insertSearchEmployee("EMP-%03d".formatted(number), (number % 2 == 1 ? "Matched" : "Other") + " Person " + number);
        }
        insertSearchEmployee("EMP-MATCH", "Unrelated Name");
        List<String> listed = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode result = assertPage(get(URI.create(searchUri("match") + "&page=" + page + "&size=2")), page, 2, 5, 3);
            assertEquals(page < 2 ? 2 : 1, result.path("items").size());
            result.path("items").valueStream().map(item -> item.path("employeeNumber").asString()).forEach(listed::add);
        }
        assertEquals(List.of("EMP-001", "EMP-003", "EMP-005", "EMP-007", "EMP-MATCH"), listed);
        assertEquals(0, assertPage(get(URI.create(searchUri("match") + "&page=3&size=2")), 3, 2, 5, 3).path("items").size());
        assertEquals(0, assertPage(get(URI.create(searchUri("match") + "&page=2147483647&size=100")),
                Integer.MAX_VALUE, 100, 5, 1).path("items").size());
        assertSearchMatches("no matching employee");
    }

    @ParameterizedTest
    @ValueSource(strings = {"q=alpha&q=beta", "q=alpha&q=alpha", "q=&q="})
    void rejectsRepeatedSearchParameters(String query) throws Exception {
        JsonNode problem = assertProblem(get(URI.create(endpoint + "?" + query)), 400, "VALIDATION_ERROR");
        assertEquals("q", problem.path("errors").get(0).path("field").asString());
    }

    @Test
    void authenticatesBeforeSearchValidation() throws Exception {
        cookies.getCookieStore().removeAll();
        assertProblem(get(searchUri("private search")), 401, "AUTHENTICATION_REQUIRED");
        assertProblem(get(searchUri("a".repeat(201))), 401, "AUTHENTICATION_REQUIRED");
    }

    private URI searchUri(String term) {
        return URI.create(endpoint + "?q=" + URLEncoder.encode(term, StandardCharsets.UTF_8));
    }

    private void assertSearchMatches(String term, String... numbers) throws Exception {
        JsonNode result = assertPage(get(searchUri(term)), 0, 25, numbers.length, numbers.length == 0 ? 0 : 1);
        assertEquals(List.of(numbers), result.path("items").valueStream()
                .map(item -> item.path("employeeNumber").asString()).toList());
    }

    private void insertSearchEmployee(String number, String name) {
        jdbc.update("""
                INSERT INTO employees (employee_number, full_name, department_id, country_code, salary_amount, currency_code)
                VALUES (?, ?, ?, 'IN', 12.345, 'KWD')
                """, number, name, departmentId);
    }

    private void insertListingEmployees(int count) {
        // Reverse insertion order makes ordering by employee number observable.
        for (int number = count; number > 0; number--) {
            jdbc.update("""
                    INSERT INTO employees (employee_number, full_name, department_id, country_code, salary_amount, currency_code)
                    VALUES (?, ?, ?, 'IN', 12.345, 'KWD')
                    """, "EMP-%03d".formatted(number), "Zoë 李 " + number, departmentId);
        }
    }

    private List<String> employeeNumbers(int start, int end) {
        return IntStream.range(start, end).mapToObj(number -> "EMP-%03d".formatted(number)).toList();
    }

    private JsonNode assertPage(HttpResponse<String> response, int page, int size, long totalElements, long totalPages) {
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        JsonNode body = JSON.readTree(response.body());
        assertEquals(5, body.size());
        assertTrue(body.path("items").isArray());
        for (String field : List.of("page", "size", "totalElements", "totalPages")) {
            assertTrue(body.path(field).isIntegralNumber(), field);
        }
        assertEquals(page, body.path("page").asInt());
        assertEquals(size, body.path("size").asInt());
        assertEquals(totalElements, body.path("totalElements").asLong());
        assertEquals(totalPages, body.path("totalPages").asLong());
        return body;
    }

    @Test
    void replacesAllEditableValuesAndReturnsNewVersionWithPreservedIdentity() throws Exception {
        JsonNode original = createTarget();
        String id = original.path("id").asString();
        long finance = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Finance'", Long.class);
        ObjectNode request = updateRequest().put("employeeNumber", " emp-99 ").put("fullName", "  Zoë  李  ")
                .put("departmentId", Long.toString(finance)).put("countryCode", " us ")
                .put("currencyCode", " kwd ").put("salaryAmount", "12.3450");
        HttpResponse<String> response = put(id, request);
        assertEquals(200, response.statusCode());
        JsonNode updated = JSON.readTree(response.body());
        assertEquals(id, updated.path("id").asString());
        assertEquals("EMP-99", updated.path("employeeNumber").asString());
        assertEquals("Zoë  李", updated.path("fullName").asString());
        assertEquals(Long.toString(finance), updated.path("departmentId").asString());
        assertEquals("Finance", updated.path("departmentName").asString());
        assertEquals("US", updated.path("countryCode").asString());
        assertEquals("United States", updated.path("countryName").asString());
        assertEquals("KWD", updated.path("currencyCode").asString());
        assertEquals("12.345", updated.path("salaryAmount").asString());
        assertEquals("1", updated.path("version").asString());
        assertEquals(original.path("createdAt"), updated.path("createdAt"));
        assertTrue(Instant.parse(updated.path("updatedAt").asString()).isAfter(Instant.parse(original.path("updatedAt").asString())));
        assertEquals(updated, JSON.readTree(get(URI.create(endpoint + "/" + id)).body()));
        assertEquals(1L, countEmployees());
    }

    @Test
    void unchangedNormalizedSubmissionPreservesVersionAndTimestamps() throws Exception {
        JsonNode original = createTarget();
        String id = original.path("id").asString();
        var before = jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", Long.parseLong(id));
        HttpResponse<String> response = put(id, updateRequest().put("employeeNumber", " emp-1 ")
                .put("fullName", " Asha Rao ").put("countryCode", " in ").put("currencyCode", " usd ")
                .put("salaryAmount", "1000.0000"));
        assertEquals(200, response.statusCode());
        assertEquals(original, JSON.readTree(response.body()));
        assertEquals(before, jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", Long.parseLong(id)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"employeeNumber", "fullName", "departmentId", "countryCode", "salaryAmount", "currencyCode", "version"})
    void updateRequiresEveryField(String field) throws Exception {
        String id = createTarget().path("id").asString();
        ObjectNode request = updateRequest();
        request.remove(field);
        assertUpdateFieldError(put(id, request), id, field, "REQUIRED");
        assertUpdateFieldError(put(id, updateRequest().putNull(field)), id, field, "REQUIRED");
        assertUpdateFieldError(put(id, updateRequest().put(field, " ")), id, field, "REQUIRED");
        assertEquals("0", JSON.readTree(get(URI.create(endpoint + "/" + id)).body()).path("version").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "+0", "0.0", "0e0", " 0", "0 ", "abc", "9223372036854775808"})
    void rejectsMalformedAndOutOfRangeOriginalVersions(String version) throws Exception {
        String id = createTarget().path("id").asString();
        JsonNode problem = assertProblem(put(id, updateRequest().put("version", version)), 400,
                "VALIDATION_ERROR", "/api/v1/employees/" + id);
        assertEquals("version", problem.path("errors").get(0).path("field").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "false", "[]", "{}"})
    void originalVersionMustBeAJsonString(String value) throws Exception {
        String id = createTarget().path("id").asString();
        ObjectNode request = updateRequest();
        request.set("version", JSON.readTree(value));
        assertProblem(put(id, request), 400, "INVALID_REQUEST", "/api/v1/employees/" + id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "createdAt", "updatedAt", "unexpected"})
    void rejectsServerManagedAndUnknownUpdateFields(String field) throws Exception {
        String id = createTarget().path("id").asString();
        assertProblem(put(id, updateRequest().put(field, "client-controlled")), 400,
                "INVALID_REQUEST", "/api/v1/employees/" + id);
    }

    @ParameterizedTest
    @CsvSource({"departmentId,9223372036854775807,UNKNOWN_REFERENCE", "countryCode,ZZ,UNKNOWN_REFERENCE",
            "currencyCode,ZZZ,UNKNOWN_REFERENCE", "salaryAmount,1.001,EXCESS_PRECISION", "salaryAmount,0,MUST_BE_POSITIVE"})
    void invalidReplacementRollsBackAllEmployeeChanges(String field, String value, String code) throws Exception {
        String id = createTarget().path("id").asString();
        var before = jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", Long.parseLong(id));
        assertUpdateFieldError(put(id, updateRequest().put("fullName", "Changed Name")
                .put("employeeNumber", "EMP-CHANGED").put(field, value)), id, field, code);
        assertEquals(before, jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", Long.parseLong(id)));
    }

    @Test
    void currencyAndAmountAreValidatedTogetherWithoutConversion() throws Exception {
        String id = createTarget().path("id").asString();
        assertUpdateFieldError(put(id, updateRequest().put("currencyCode", "JPY").put("salaryAmount", "12.34")),
                id, "salaryAmount", "EXCESS_PRECISION");
        HttpResponse<String> response = put(id, updateRequest().put("currencyCode", "JPY").put("salaryAmount", "1200.0000"));
        assertEquals(200, response.statusCode());
        assertEquals("1200", JSON.readTree(response.body()).path("salaryAmount").asString());
        assertEquals("JPY", JSON.readTree(response.body()).path("currencyCode").asString());
    }

    @Test
    void duplicateNumberRollsBackReplacementWithoutAffectingEitherEmployee() throws Exception {
        String id = createTarget().path("id").asString();
        assertEquals(201, post(validRequest().put("employeeNumber", "EMP-2").toString()).statusCode());
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        assertProblem(put(id, updateRequest().put("employeeNumber", " emp-2 ").put("fullName", "Changed")),
                409, "EMPLOYEE_NUMBER_CONFLICT", "/api/v1/employees/" + id);
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @Test
    void staleFormCannotOverwriteNewerValuesEvenWhenSubmittingOldUnchangedValues() throws Exception {
        String id = createTarget().path("id").asString();
        HttpResponse<String> first = put(id, updateRequest().put("salaryAmount", "2000"));
        assertEquals(200, first.statusCode());
        assertProblem(put(id, updateRequest()), 409, "STALE_EMPLOYEE", "/api/v1/employees/" + id);
        assertEquals(JSON.readTree(first.body()), JSON.readTree(get(URI.create(endpoint + "/" + id)).body()));
        assertProblem(put(id, updateRequest().put("version", "9223372036854775807")),
                409, "STALE_EMPLOYEE", "/api/v1/employees/" + id);
    }

    @Test
    void missingUpdateTargetReturnsNotFoundWithoutInsertingAnEmployee() throws Exception {
        assertProblem(put("9223372036854775807", updateRequest()), 404, "EMPLOYEE_NOT_FOUND",
                "/api/v1/employees/9223372036854775807");
        assertEquals(0L, countEmployees());
    }

    @Test
    void concurrentHttpUpdatesProduceOneSuccessAndOneStaleConflict() throws Exception {
        String id = createTarget().path("id").asString();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return put(id, updateRequest().put("salaryAmount", "2000")); });
            var second = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return put(id, updateRequest().put("salaryAmount", "3000")); });
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            HttpResponse<String> winner = responses.stream().filter(response -> response.statusCode() == 200).findFirst().orElseThrow();
            HttpResponse<String> loser = responses.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow();
            assertProblem(loser, 409, "STALE_EMPLOYEE", "/api/v1/employees/" + id);
            assertEquals("1", JSON.readTree(winner.body()).path("version").asString());
            assertEquals(JSON.readTree(winner.body()), JSON.readTree(get(URI.create(endpoint + "/" + id)).body()));
        }
    }

    @Test
    void updateRequiresAuthenticationAndCsrf() throws Exception {
        String id = createTarget().path("id").asString();
        URI uri = URI.create(endpoint + "/" + id);
        HttpRequest request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateRequest().toString())).build();
        assertProblem(client.send(request, HttpResponse.BodyHandlers.ofString()), 403, "CSRF_INVALID", uri.getPath());
        cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .toList().forEach(cookie -> cookies.getCookieStore().remove(endpoint, cookie));
        assertProblem(put(id, updateRequest()), 401, "AUTHENTICATION_REQUIRED", uri.getPath());
    }

    private JsonNode createTarget() throws Exception {
        HttpResponse<String> response = post(validRequest().toString());
        assertEquals(201, response.statusCode());
        return JSON.readTree(response.body());
    }

    private ObjectNode updateRequest() {
        return validRequest().put("version", "0");
    }

    private HttpResponse<String> put(String id, ObjectNode body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(endpoint + "/" + id)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", token)
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertUpdateFieldError(HttpResponse<String> response, String id, String field, String code) {
        JsonNode errors = assertProblem(response, 400, "VALIDATION_ERROR", "/api/v1/employees/" + id).path("errors");
        assertEquals(field, errors.get(0).path("field").asString());
        assertEquals(code, errors.get(0).path("code").asString());
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
