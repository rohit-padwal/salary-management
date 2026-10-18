package com.acme.salarymanagement.employee;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.acme.salarymanagement.SalaryManagementApplication;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
        List<CreateEmployeeCommand> invalid = List.of(
                new CreateEmployeeCommand("EMP-1", "Name", Long.MAX_VALUE, "IN", "1000", "USD"),
                new CreateEmployeeCommand("EMP-1", "Name", departmentId, "ZZ", "1000", "USD"),
                new CreateEmployeeCommand("EMP-1", "Name", departmentId, "IN", "1000", "ZZZ"));
        List<String> fields = List.of("departmentId", "countryCode", "currencyCode");
        for (int i = 0; i < invalid.size(); i++) {
            CreateEmployeeCommand command = invalid.get(i);
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

    private CreateEmployeeCommand command(String number, String name, String salary, String currency) {
        return new CreateEmployeeCommand(number, name, departmentId, " in ", salary, currency);
    }

    private long countEmployees() {
        return jdbc.queryForObject("SELECT count(*) FROM employees", Long.class);
    }
}
