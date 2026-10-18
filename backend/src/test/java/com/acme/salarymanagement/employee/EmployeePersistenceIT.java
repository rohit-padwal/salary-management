package com.acme.salarymanagement.employee;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.acme.salarymanagement.SalaryManagementApplication;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeePersistenceIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static ConfigurableApplicationContext context;
    private static EmployeeCreationService service;
    private static JdbcTemplate jdbc;
    private long departmentId;

    @BeforeAll
    static void startApplicationWithIsolatedDatabase() {
        POSTGRES.start();
        context = new SpringApplicationBuilder(SalaryManagementApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
        service = context.getBean(EmployeeCreationService.class);
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
    void clearTestEmployees() {
        jdbc.update("DELETE FROM employees");
        departmentId = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Engineering'", Long.class);
    }

    @Test
    void createsAndCommitsNormalizedEmployeeWithExactSalaryAndServerMetadata() {
        EmployeeResult result = service.create(command(" emp-1 ", "  Zoë  李  ", "125000.1200", " usd "));

        assertTrue(result.id() > 0);
        assertEquals("EMP-1", result.employeeNumber());
        assertEquals("Zoë  李", result.fullName());
        assertEquals(departmentId, result.departmentId());
        assertEquals("Engineering", result.departmentName());
        assertEquals("IN", result.countryCode());
        assertEquals("India", result.countryName());
        assertEquals("USD", result.currencyCode());
        assertEquals(new BigDecimal("125000.12"), result.salaryAmount());
        assertEquals(0L, result.version());
        assertNotNull(result.createdAt());
        assertEquals(result.createdAt(), result.updatedAt());

        jdbc.queryForObject("SELECT * FROM employees WHERE id = ?", (row, index) -> {
            assertEquals("EMP-1", row.getString("employee_number"));
            assertEquals("Zoë  李", row.getString("full_name"));
            assertEquals(0, result.salaryAmount().compareTo(row.getBigDecimal("salary_amount")));
            assertEquals(result.createdAt(), row.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
            return row.getLong("id");
        }, result.id());
        assertEquals(1L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"JPY,1200.0000,1200", "KWD,12.3450,12.345", "USD,999999999999999.99,999999999999999.99"})
    void usesSupportedCurrencyPrecisionIndependentlyOfEmploymentCountry(String currency, String input, String expected) {
        EmployeeResult result = service.create(command("EMP-1", "Name", input, currency));
        assertEquals(new BigDecimal(expected), result.salaryAmount());
        assertEquals(1L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"JPY,1.1", "KWD,1.2345", "USD,1.001", "USD,0", "USD,1000000000000000"})
    void rejectsInvalidCompensationBeforeAnyEmployeeIsWritten(String currency, String salary) {
        assertEquals("salaryAmount", assertThrows(InputValidationException.class,
                () -> service.create(command("EMP-1", "Name", salary, currency))).field());
        assertEquals(0L, countEmployees());
    }

    @Test
    void rejectsUnknownReferencesWithoutWritingEmployee() {
        List<EmployeeDetails> invalid = List.of(
                new EmployeeDetails("EMP-1", "Name", Long.MAX_VALUE, "IN", "1000", "USD"),
                new EmployeeDetails("EMP-1", "Name", departmentId, "ZZ", "1000", "USD"),
                new EmployeeDetails("EMP-1", "Name", departmentId, "IN", "1000", "ZZZ"));
        List<String> fields = List.of("departmentId", "countryCode", "currencyCode");
        for (int i = 0; i < invalid.size(); i++) {
            EmployeeDetails command = invalid.get(i);
            InputValidationException error = assertThrows(InputValidationException.class, () -> service.create(command));
            assertEquals(fields.get(i), error.field());
            assertEquals("UNKNOWN_REFERENCE", error.code());
            assertEquals(0L, countEmployees());
        }
    }

    @Test
    void rejectsInvalidIdentityWithoutWritingEmployee() {
        assertThrows(InputValidationException.class,
                () -> service.create(command("-INVALID", "Name", "1000", "USD")));
        assertEquals(0L, countEmployees());
    }

    @Test
    void persistsMaximumLengthInternationalName() {
        String name = "𠀀".repeat(200);
        EmployeeResult result = service.create(command("EMP-1", name, "1000", "USD"));
        assertEquals(name, jdbc.queryForObject("SELECT full_name FROM employees WHERE id = ?", String.class, result.id()));
    }

    @Test
    void reportsNormalizedDuplicateAndLeavesOriginalEmployeeUnchanged() {
        EmployeeResult original = service.create(command("EMP-1", "Original Name", "1000", "USD"));
        EmployeeNumberConflictException error = assertThrows(EmployeeNumberConflictException.class,
                () -> service.create(command(" emp-1 ", "Replacement Name", "2000", "USD")));
        assertEquals("Employee number is already in use.", error.getMessage());
        assertFalse(error.getMessage().contains("EMP-1"));
        assertEquals(1L, countEmployees());
        assertEquals("Original Name", jdbc.queryForObject("SELECT full_name FROM employees WHERE id = ?", String.class, original.id()));
        // The failed transaction must not poison subsequent requests.
        service.create(command("EMP-2", "Another Name", "1500", "USD"));
        assertEquals(2L, countEmployees());
    }

    @Test
    void concurrentRequestsForTheSameEmployeeNumberProduceOneCreationAndOneConflict() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<String> create = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                service.create(command("EMP-1", "Name", "1000", "USD"));
                return "created";
            } catch (EmployeeNumberConflictException conflict) {
                return "conflict";
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(create);
            var second = executor.submit(create);
            List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1L, outcomes.stream().filter("created"::equals).count());
            assertEquals(1L, outcomes.stream().filter("conflict"::equals).count());
        }
        assertEquals(1L, countEmployees());
    }

    @Test
    void retrievesEmployeeAndAllLookupLabelsWithOneSelectAndNoWrites() {
        EmployeeResult created = service.create(command("EMP-1", "Zoë  李", "12.3450", "KWD"));
        var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean originallyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            EmployeeResult fetched = context.getBean(EmployeeQueryService.class).getById(created.id());
            assertEquals(created, fetched);
            assertEquals(1, statistics.getPrepareStatementCount());
            assertEquals(0, statistics.getEntityInsertCount());
            assertEquals(0, statistics.getEntityUpdateCount());
            assertEquals(0, statistics.getEntityDeleteCount());
        } finally {
            statistics.setStatisticsEnabled(originallyEnabled);
        }
    }

    @Test
    void missingEmployeeProducesBusinessNotFoundWithoutWriting() {
        assertThrows(EmployeeNotFoundException.class,
                () -> context.getBean(EmployeeQueryService.class).getById(Long.MAX_VALUE));
        assertEquals(0L, countEmployees());
    }

    @ParameterizedTest
    @CsvSource({"0,3,3,2", "3,3,1,2", "4,3,0,1", "2147483647,100,0,1"})
    void listingLoadsOnlyTheRequestedEmployeesAndLabelsWithBoundedSelects(
            int page, int size, int expectedItems, int expectedStatements) {
        for (int number = 10; number > 0; number--) {
            service.create(command("EMP-%02d".formatted(number), "Name " + number, "12.345", "KWD"));
        }
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean originallyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            EmployeePageResult result = context.getBean(EmployeeQueryService.class).list(new EmployeePageRequest(page, size));
            assertEquals(expectedItems, result.items().size());
            assertEquals(10, result.totalElements());
            assertEquals(size == 3 ? 4 : 1, result.totalPages());
            if (!result.items().isEmpty()) {
                EmployeeResult first = result.items().getFirst();
                assertEquals("EMP-%02d".formatted(page * size + 1), first.employeeNumber());
                assertEquals("Engineering", first.departmentName());
                assertEquals("India", first.countryName());
                assertEquals(new BigDecimal("12.345"), first.salaryAmount());
            }
            assertEquals(expectedStatements, statistics.getPrepareStatementCount());
            assertEquals(expectedItems, statistics.getEntityStatistics(Employee.class.getName()).getLoadCount());
            assertEquals(0, statistics.getEntityInsertCount());
            assertEquals(0, statistics.getEntityUpdateCount());
            assertEquals(0, statistics.getEntityDeleteCount());
        } finally {
            statistics.setStatisticsEnabled(originallyEnabled);
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @ParameterizedTest
    @CsvSource({"0,2,2,2", "2,2,1,2", "3,2,0,1", "2147483647,100,0,1"})
    void literalSearchUsesTheSamePredicateForCountAndBoundedPageReads(
            int page, int size, int expectedItems, int expectedStatements) {
        for (int number = 10; number > 0; number--) {
            String name = number % 2 == 1 ? "Chosen%_!\\ Person " : "ChosenAnything Person ";
            service.create(command("EMP-%02d".formatted(number), name + number, "12.345", "KWD"));
        }
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean originallyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            EmployeePageResult result = context.getBean(EmployeeQueryService.class)
                    .list(new EmployeePageRequest(page, size, " ChOsEn%_!\\ "));
            assertEquals(5, result.totalElements());
            assertEquals(size == 2 ? 3 : 1, result.totalPages());
            assertEquals(expectedItems, result.items().size());
            if (!result.items().isEmpty()) {
                EmployeeResult first = result.items().getFirst();
                assertEquals("EMP-%02d".formatted(page * size * 2 + 1), first.employeeNumber());
                assertEquals("Engineering", first.departmentName());
                assertEquals("India", first.countryName());
                assertEquals(new BigDecimal("12.345"), first.salaryAmount());
            }
            assertEquals(expectedStatements, statistics.getPrepareStatementCount());
            assertEquals(expectedItems, statistics.getEntityStatistics(Employee.class.getName()).getLoadCount());
            assertEquals(0, statistics.getEntityInsertCount());
            assertEquals(0, statistics.getEntityUpdateCount());
            assertEquals(0, statistics.getEntityDeleteCount());
        } finally {
            statistics.setStatisticsEnabled(originallyEnabled);
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @ParameterizedTest
    @CsvSource({"0,2,2,5", "2,2,1,5", "3,2,0,4", "2147483647,100,0,4"})
    void filteredSearchAddsOnlyCatalogValidationToBoundedEmployeeQueries(
            int page, int size, int expectedItems, int expectedStatements) {
        for (int number = 10; number > 0; number--) {
            String name = number % 2 == 1 ? "Match Person " : "Other Person ";
            service.create(command("EMP-%02d".formatted(number), name + number, "1000", "USD"));
        }
        long finance = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Finance'", Long.class);
        service.create(new EmployeeDetails("OUT-DEPARTMENT", "Match Person", finance, "IN", "1000", "USD"));
        service.create(new EmployeeDetails("OUT-COUNTRY", "Match Person", departmentId, "US", "1000", "USD"));
        service.create(new EmployeeDetails("OUT-CURRENCY", "Match Person", departmentId, "IN", "1000", "INR"));
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
        boolean originallyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            EmployeePageResult result = context.getBean(EmployeeQueryService.class)
                    .list(new EmployeePageRequest(page, size, "mAtCh", departmentId, " in ", " usd "));
            assertEquals(5, result.totalElements());
            assertEquals(size == 2 ? 3 : 1, result.totalPages());
            assertEquals(expectedItems, result.items().size());
            if (!result.items().isEmpty()) {
                EmployeeResult first = result.items().getFirst();
                assertEquals("EMP-%02d".formatted(page * size * 2 + 1), first.employeeNumber());
                assertEquals("Engineering", first.departmentName());
                assertEquals("India", first.countryName());
                assertEquals("USD", first.currencyCode());
                assertEquals(new BigDecimal("1000.00"), first.salaryAmount());
            }
            assertEquals(expectedStatements, statistics.getPrepareStatementCount());
            assertEquals(expectedItems, statistics.getEntityStatistics(Employee.class.getName()).getLoadCount());
            assertEquals(0, statistics.getEntityInsertCount());
            assertEquals(0, statistics.getEntityUpdateCount());
            assertEquals(0, statistics.getEntityDeleteCount());
        } finally {
            statistics.setStatisticsEnabled(originallyEnabled);
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @Test
    void twoTransactionsUsingTheSameLoadedVersionCannotBothCommit() throws Exception {
        EmployeeResult original = service.create(command("EMP-1", "Name", "1000", "USD"));
        CyclicBarrier loaded = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> raceUpdate(original.id(), "2000", loaded));
            var second = executor.submit(() -> raceUpdate(original.id(), "3000", loaded));
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1L, outcomes.stream().filter("updated"::equals).count());
            assertEquals(1L, outcomes.stream().filter("stale"::equals).count());
        }
        EmployeeResult stored = context.getBean(EmployeeQueryService.class).getById(original.id());
        assertEquals(1L, stored.version());
        assertTrue(List.of(new BigDecimal("2000.00"), new BigDecimal("3000.00")).contains(stored.salaryAmount()));
        assertEquals(1L, countEmployees());
    }

    @Test
    void updatesEmployeeAtomicallyAndPreservesCreationMetadata() {
        EmployeeResult original = service.create(command("EMP-1", "Name", "1000", "USD"));
        EmployeeResult updated = context.getBean(EmployeeUpdateService.class).update(original.id(),
                new UpdateEmployeeCommand(command("EMP-1", "Changed Name", "2000", "USD"), 0));
        assertEquals(original.id(), updated.id());
        assertEquals(original.createdAt(), updated.createdAt());
        assertEquals(1L, updated.version());
        assertEquals(new BigDecimal("2000.00"), updated.salaryAmount());
        assertEquals("Changed Name", updated.fullName());
        assertEquals(updated, context.getBean(EmployeeQueryService.class).getById(original.id()));
    }

    @Test
    void unchangedSubmissionStillRejectsAConcurrentChangeAtCommit() throws Exception {
        EmployeeDetails details = command("EMP-1", "Name", "1000", "USD");
        EmployeeResult original = service.create(details);
        CountDownLatch noChangePrepared = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var noChange = executor.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                    .execute(status -> {
                        EmployeeResult result = context.getBean(EmployeeUpdateService.class)
                                .update(original.id(), new UpdateEmployeeCommand(details, 0));
                        noChangePrepared.countDown();
                        await(writerCommitted);
                        return result;
                    }));
            try {
                assertTrue(noChangePrepared.await(10, TimeUnit.SECONDS));
                context.getBean(EmployeeUpdateService.class).update(original.id(),
                        new UpdateEmployeeCommand(command("EMP-1", "Name", "2000", "USD"), 0));
            } finally {
                writerCommitted.countDown();
            }
            ExecutionException failure = assertThrows(ExecutionException.class, () -> noChange.get(15, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof OptimisticLockingFailureException
                    || failure.getCause() instanceof OptimisticLockException, failure.getCause().getClass().getName());
        }
        EmployeeResult stored = context.getBean(EmployeeQueryService.class).getById(original.id());
        assertEquals(new BigDecimal("2000.00"), stored.salaryAmount());
        assertEquals(1L, stored.version());
    }

    private String raceUpdate(long id, String salary, CyclicBarrier loaded) {
        try {
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                context.getBean(EmployeeRepository.class).findById(id).orElseThrow();
                try {
                    loaded.await(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("Could not synchronize test transactions", failure);
                }
                return context.getBean(EmployeeUpdateService.class).update(id,
                        new UpdateEmployeeCommand(command("EMP-1", "Name", salary, "USD"), 0));
            });
            return "updated";
        } catch (StaleEmployeeException | OptimisticLockingFailureException | OptimisticLockException conflict) {
            return "stale";
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test transaction interrupted", interrupted);
        }
    }

    private EmployeeDetails command(String number, String name, String salary, String currency) {
        return new EmployeeDetails(number, name, departmentId, " in ", salary, currency);
    }

    private long countEmployees() {
        return jdbc.queryForObject("SELECT count(*) FROM employees", Long.class);
    }
}
