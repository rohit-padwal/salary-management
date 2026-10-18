package com.acme.salarymanagement.analytics.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.acme.salarymanagement.SalaryManagementApplication;
import com.acme.salarymanagement.analytics.model.CompensationAggregate;
import com.acme.salarymanagement.analytics.model.CompensationAnalyticsResult;
import com.acme.salarymanagement.analytics.model.CompensationGrouping;
import com.acme.salarymanagement.analytics.model.CompensationQuery;
import com.acme.salarymanagement.analytics.repository.CompensationAggregateRepository;
import com.acme.salarymanagement.employee.model.EmployeePageRequest;
import com.acme.salarymanagement.employee.model.EmployeeReadCriteria;
import com.acme.salarymanagement.employee.service.EmployeeQueryService;
import com.acme.salarymanagement.employee.validation.EmployeeCriteriaValidator;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CompensationAnalyticsIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static ConfigurableApplicationContext context;
    private static CompensationAnalyticsService service;
    private static JdbcTemplate jdbc;
    private long engineering;
    private long finance;

    @BeforeAll
    static void startIsolatedApplication() {
        POSTGRES.start();
        context = new SpringApplicationBuilder(SalaryManagementApplication.class).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
        service = context.getBean(CompensationAnalyticsService.class);
        jdbc = context.getBean(JdbcTemplate.class);
    }

    @AfterAll
    static void closeApplicationAndDatabase() {
        if (context != null) {
            context.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void clearOnlyIsolatedTestEmployees() {
        jdbc.update("DELETE FROM employees");
        jdbc.update("DELETE FROM departments WHERE name LIKE 'Test Analytics %'");
        engineering = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Engineering'", Long.class);
        finance = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Finance'", Long.class);
    }

    @Test
    void groupsBothDimensionsAndSeparatesCurrencies() {
        insertCompensationFixture();
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        CompensationAnalyticsResult departments = service.analyze(new CompensationQuery(null, null));
        assertEquals(CompensationGrouping.DEPARTMENT, departments.groupBy());
        assertEquals(8, departments.totalHeadcount());
        assertEquals(List.of(
                group(Long.toString(engineering), "Engineering", "JPY", 1, "1000", "1000", "1000", "1000"),
                group(Long.toString(engineering), "Engineering", "KWD", 1, "1.234", "1.234", "1.234", "1.234"),
                group(Long.toString(engineering), "Engineering", "USD", 1, "100.00", "100.00", "100.00", "100.00"),
                group(Long.toString(finance), "Finance", "JPY", 1, "1001", "1001", "1001", "1001"),
                group(Long.toString(finance), "Finance", "KWD", 1, "1.235", "1.235", "1.235", "1.235"),
                group(Long.toString(finance), "Finance", "USD", 3, "30.00", "10.00", "10.00", "10.00")), departments.groups());
        assertEquals(List.of(
                summary("JPY", 2, "2001", "1000", "1000", "1001"),
                summary("KWD", 2, "2.469", "1.234", "1.234", "1.235"),
                summary("USD", 4, "130.00", "32.50", "10.00", "100.00")), departments.currencySummaries());

        CompensationAnalyticsResult countries = service.analyze(new CompensationQuery(CompensationGrouping.COUNTRY, null));
        assertEquals(CompensationGrouping.COUNTRY, countries.groupBy());
        assertEquals(departments.totalHeadcount(), countries.totalHeadcount());
        assertEquals(departments.currencySummaries(), countries.currencySummaries());
        assertEquals(List.of(
                group("IN", "India", "JPY", 1, "1000", "1000", "1000", "1000"),
                group("IN", "India", "KWD", 1, "1.235", "1.235", "1.235", "1.235"),
                group("IN", "India", "USD", 2, "110.00", "55.00", "10.00", "100.00"),
                group("JP", "Japan", "JPY", 1, "1001", "1001", "1001", "1001"),
                group("KW", "Kuwait", "KWD", 1, "1.234", "1.234", "1.234", "1.234"),
                group("US", "United States", "USD", 2, "20.00", "10.00", "10.00", "10.00")), countries.groups());
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @ParameterizedTest
    @CsvSource({"0,8", "1,4", "2,4", "3,2", "4,4", "5,2", "6,2", "7,1"})
    void everyFilterCombinationAndSearchAgreesWithEmployeeListing(int filters, long expectedCount) {
        for (int combination = 7; combination >= 0; combination--) {
            boolean matchesNumber = combination % 2 == 1;
            insert((matchesNumber ? "MATCH-" : "EMP-") + combination,
                    (matchesNumber ? "Other Person " : "Match Person ") + combination,
                    (combination & 1) != 0 ? engineering : finance,
                    (combination & 2) != 0 ? "IN" : "US", (combination & 4) != 0 ? "USD" : "INR", "100");
        }
        for (String term : new String[]{null, " MaTcH "}) {
            EmployeeReadCriteria criteria = new EmployeeReadCriteria(term, (filters & 1) != 0 ? engineering : null,
                    (filters & 2) != 0 ? " in " : null, (filters & 4) != 0 ? " usd " : null);
            long listed = context.getBean(EmployeeQueryService.class).list(new EmployeePageRequest(0, 100,
                    criteria.q(), criteria.departmentId(), criteria.countryCode(), criteria.currencyCode())).totalElements();
            for (CompensationGrouping grouping : CompensationGrouping.values()) {
                CompensationAnalyticsResult result = service.analyze(new CompensationQuery(grouping, criteria));
                assertEquals(expectedCount, result.totalHeadcount());
                assertEquals(listed, result.totalHeadcount());
                assertEquals(expectedCount, result.groups().stream().mapToLong(CompensationAnalyticsResult.Group::headcount).sum());
                for (var currency : result.currencySummaries()) {
                    assertEquals(new BigDecimal("100.00").multiply(BigDecimal.valueOf(currency.headcount())), currency.totalSalary());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "'", "%_!\\", "' OR 1=1 --", "ZOË", "李"})
    void literalSearchAndUnicodeMatchingAgreeWithListing(String term) {
        insert("EMP-1", "Literal " + (term.equals("ZOË") ? "Zoë" : term) + " Person", engineering, "IN", "USD", "10");
        insert("EMP-2", "Literal Any Other Person", engineering, "IN", "USD", "20");
        EmployeeReadCriteria criteria = new EmployeeReadCriteria(term, null, null, null);
        CompensationAnalyticsResult result = service.analyze(new CompensationQuery(null, criteria));
        assertEquals(1, result.totalHeadcount());
        assertEquals(new BigDecimal("10.00"), result.currencySummaries().getFirst().totalSalary());
        assertEquals(context.getBean(EmployeeQueryService.class).list(new EmployeePageRequest(0, 25, term)).totalElements(), result.totalHeadcount());
    }

    @ParameterizedTest
    @CsvSource({"USD,1000,1000.01,1000.00", "USD,1000.01,1000.02,1000.02", "JPY,1000,1001,1000",
            "JPY,1001,1002,1002", "KWD,1.234,1.235,1.234", "KWD,1.235,1.236,1.236"})
    void computesExactDatabaseAggregatesBeforeHalfEvenRounding(String currency, String first, String second, String expectedAverage) {
        insert("EMP-1", "Name One", engineering, "IN", currency, first);
        insert("EMP-2", "Name Two", engineering, "IN", currency, second);
        CompensationAnalyticsResult result = service.analyze(new CompensationQuery(null, null));
        assertEquals(new BigDecimal(expectedAverage), result.groups().getFirst().averageSalary());
        assertEquals(new BigDecimal(expectedAverage), result.currencySummaries().getFirst().averageSalary());
    }

    @Test
    void sumsBeyondTheEmployeeColumnRangeWithoutNarrowing() {
        insert("EMP-1", "Name One", engineering, "IN", "USD", "999999999999999.99");
        insert("EMP-2", "Name Two", engineering, "US", "USD", "999999999999999.99");
        CompensationAnalyticsResult result = service.analyze(new CompensationQuery(null, null));
        assertEquals(new BigDecimal("1999999999999999.98"), result.groups().getFirst().totalSalary());
        assertEquals(new BigDecimal("1999999999999999.98"), result.currencySummaries().getFirst().totalSalary());
    }

    @Test
    void emptyAndNoMatchResultsHaveNoInventedGroupsOrCurrencyStatistics() {
        assertEmpty(service.analyze(new CompensationQuery(null, null)));
        insertCompensationFixture();
        assertEmpty(service.analyze(new CompensationQuery(null, new EmployeeReadCriteria("no matches", null, null, null))));
        assertEmpty(service.analyze(new CompensationQuery(null, new EmployeeReadCriteria(null, null, "DE", "USD"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departmentId", "countryCode", "currencyCode"})
    void unsupportedCatalogFiltersAreErrorsEvenWhenThereAreNoEmployees(String field) {
        EmployeeReadCriteria criteria = new EmployeeReadCriteria(null, field.equals("departmentId") ? Long.MAX_VALUE : null,
                field.equals("countryCode") ? "ZZ" : null, field.equals("currencyCode") ? "ZZZ" : null);
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> service.analyze(new CompensationQuery(null, criteria)));
        assertEquals(field, failure.field());
        assertEquals("UNKNOWN_REFERENCE", failure.code());
    }

    @Test
    void departmentKeysRemainExactStringsAndSortNumerically() {
        jdbc.update("INSERT INTO departments (id, name) OVERRIDING SYSTEM VALUE VALUES (10, 'Test Analytics Ten'), (9007199254740993, 'Test Analytics Large')");
        insert("EMP-1", "Name One", 9007199254740993L, "IN", "USD", "1");
        insert("EMP-2", "Name Two", 10, "IN", "USD", "2");
        insert("EMP-3", "Name Three", finance, "IN", "USD", "3");
        var result = service.analyze(new CompensationQuery(null, null));
        assertEquals(List.of(Long.toString(finance), "10", "9007199254740993"), result.groups().stream()
                .map(CompensationAnalyticsResult.Group::groupKey).toList());
    }

    @Test
    void oneDatabaseAggregationReturnsGroupsInsteadOfLoadingEmployeeEntities() {
        jdbc.update("""
                INSERT INTO employees (employee_number, full_name, department_id, country_code, currency_code, salary_amount)
                SELECT 'EMP-' || n, 'Synthetic Person ' || n, ?, 'IN', 'USD', 10
                FROM generate_series(1, 1000) AS n
                """, engineering);
        JdbcTemplate tracked = spy(jdbc);
        var repository = new CompensationAggregateRepository(tracked);
        var rows = repository.aggregate(CompensationGrouping.DEPARTMENT, EmployeeReadCriteria.EMPTY);
        verify(tracked, times(1)).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CompensationAggregate>>any(), any(Object[].class));
        assertEquals(1, rows.size());
        assertEquals(1000, rows.getFirst().headcount());
        var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean originallyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var result = service.analyze(new CompensationQuery(null, null));
            assertEquals(1000, result.totalHeadcount());
            assertEquals(new BigDecimal("10000.00"), result.currencySummaries().getFirst().totalSalary());
            assertEquals(0, statistics.getEntityLoadCount());
            assertEquals(0, statistics.getEntityInsertCount());
            assertEquals(0, statistics.getEntityUpdateCount());
            assertEquals(0, statistics.getEntityDeleteCount());
        } finally {
            statistics.setStatisticsEnabled(originallyEnabled);
        }
    }

    @Test
    void groupAndCurrencySummariesKeepTheSameReadWhenAnotherTransactionWritesAfterAggregation() throws Exception {
        insert("EMP-1", "Name One", engineering, "IN", "USD", "100");
        var repository = spy(new CompensationAggregateRepository(jdbc));
        CountDownLatch aggregateRead = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object rows = invocation.callRealMethod();
            aggregateRead.countDown();
            assertTrue(writerCommitted.await(10, TimeUnit.SECONDS));
            return rows;
        }).when(repository).aggregate(eq(CompensationGrouping.DEPARTMENT), eq(EmployeeReadCriteria.EMPTY));
        var isolatedService = new CompensationAnalyticsService(repository, context.getBean(EmployeeCriteriaValidator.class));
        TransactionTemplate read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> read.execute(status -> isolatedService.analyze(new CompensationQuery(null, null))));
            try {
                assertTrue(aggregateRead.await(10, TimeUnit.SECONDS));
                jdbc.update("UPDATE employees SET salary_amount = 200 WHERE employee_number = 'EMP-1'");
            } finally {
                writerCommitted.countDown();
            }
            var result = response.get(15, TimeUnit.SECONDS);
            assertEquals(new BigDecimal("100.00"), result.groups().getFirst().totalSalary());
            assertEquals(new BigDecimal("100.00"), result.currencySummaries().getFirst().totalSalary());
            verify(repository, times(1)).aggregate(CompensationGrouping.DEPARTMENT, EmployeeReadCriteria.EMPTY);
        }
        assertEquals(0, new BigDecimal("200").compareTo(jdbc.queryForObject("SELECT salary_amount FROM employees", BigDecimal.class)));
    }

    private void insertCompensationFixture() {
        insert("EMP-1", "Match One", engineering, "IN", "USD", "100");
        insert("EMP-2", "Match Two", finance, "US", "USD", "10");
        insert("EMP-3", "Match Three", finance, "IN", "USD", "10");
        insert("EMP-4", "Match Four", finance, "US", "USD", "10");
        insert("EMP-5", "Match Five", engineering, "IN", "JPY", "1000");
        insert("EMP-6", "Match Six", finance, "JP", "JPY", "1001");
        insert("EMP-7", "Match Seven", engineering, "KW", "KWD", "1.234");
        insert("EMP-8", "Match Eight", finance, "IN", "KWD", "1.235");
    }

    private void insert(String number, String name, long department, String country, String currency, String amount) {
        jdbc.update("""
                INSERT INTO employees (employee_number, full_name, department_id, country_code, currency_code, salary_amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """, number, name, department, country, currency, new BigDecimal(amount));
    }

    private CompensationAnalyticsResult.Group group(String key, String label, String currency, long count,
            String total, String average, String minimum, String maximum) {
        return new CompensationAnalyticsResult.Group(key, label, currency, count, new BigDecimal(total),
                new BigDecimal(average), new BigDecimal(minimum), new BigDecimal(maximum));
    }

    private CompensationAnalyticsResult.CurrencySummary summary(String currency, long count, String total,
            String average, String minimum, String maximum) {
        return new CompensationAnalyticsResult.CurrencySummary(currency, count, new BigDecimal(total),
                new BigDecimal(average), new BigDecimal(minimum), new BigDecimal(maximum));
    }

    private void assertEmpty(CompensationAnalyticsResult result) {
        assertEquals(0, result.totalHeadcount());
        assertTrue(result.groups().isEmpty());
        assertTrue(result.currencySummaries().isEmpty());
    }
}
