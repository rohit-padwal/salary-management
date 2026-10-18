package com.acme.salarymanagement.employee.seed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.acme.salarymanagement.SalaryManagementApplication;
import com.acme.salarymanagement.analytics.model.CompensationQuery;
import com.acme.salarymanagement.analytics.service.CompensationAnalyticsService;
import com.acme.salarymanagement.employee.model.EmployeePageRequest;
import com.acme.salarymanagement.employee.service.EmployeeQueryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeSeedIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static EmployeeSeedService seeds;

    @BeforeAll
    static void startIsolatedApplicationWithoutSeedingAutomatically() {
        POSTGRES.start();
        context = new SpringApplicationBuilder(SalaryManagementApplication.class).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
        jdbc = context.getBean(JdbcTemplate.class);
        seeds = context.getBean(EmployeeSeedService.class);
        assertEquals(0L, employeeCount());
    }

    @AfterAll
    static void closeApplicationAndDatabase() {
        if (context != null) {
            context.close();
        }
        POSTGRES.stop();
    }

    @BeforeEach
    void restoreOnlyIsolatedTestDatabase() {
        jdbc.execute("ALTER TABLE employees DROP CONSTRAINT IF EXISTS test_seed_late_failure");
        jdbc.execute("TRUNCATE employees, departments, countries, currencies RESTART IDENTITY");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__insert_reference_data.sql"))
                .execute(context.getBean(javax.sql.DataSource.class));
    }

    @Test
    void loadsTenThousandValidEmployeesWithWorkingListingAndAnalytics() {
        assertEquals(10_000, seeds.seed());
        assertEquals(10_000L, employeeCount());
        assertEquals(10_000L, jdbc.queryForObject("SELECT count(DISTINCT employee_number) FROM employees", Long.class));
        assertEquals(8L, jdbc.queryForObject("SELECT count(DISTINCT department_id) FROM employees", Long.class));
        assertEquals(6L, jdbc.queryForObject("SELECT count(DISTINCT country_code) FROM employees", Long.class));
        assertEquals(6L, jdbc.queryForObject("SELECT count(DISTINCT currency_code) FROM employees", Long.class));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM employees e JOIN currencies c ON c.code = e.currency_code
                WHERE e.salary_amount <= 0 OR e.salary_amount <> round(e.salary_amount, c.minor_units)
                   OR e.version <> 0 OR e.created_at IS NULL OR e.updated_at <> e.created_at
                """, Long.class));
        var page = context.getBean(EmployeeQueryService.class).list(new EmployeePageRequest(0, 100));
        assertEquals(10_000, page.totalElements());
        assertEquals(100, page.items().size());
        var analytics = context.getBean(CompensationAnalyticsService.class).analyze(new CompensationQuery(null, null));
        assertEquals(10_000, analytics.totalHeadcount());
        assertEquals(6, analytics.currencySummaries().size());
        assertEquals(10_000, analytics.currencySummaries().stream().mapToLong(summary -> summary.headcount()).sum());
    }

    @Test
    void rerunRefusesToOverwriteSeededDataOrSubsequentHrEdits() {
        seeds.seed();
        jdbc.update("UPDATE employees SET full_name = 'Edited by HR', salary_amount = 1200000 WHERE employee_number = 'SYN-000001'");
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY employee_number");
        IllegalStateException failure = assertThrows(IllegalStateException.class, seeds::seed);
        assertTrue(failure.getMessage().contains("empty employee table"));
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY employee_number"));
    }

    @Test
    void demoSeedingRunsOnceAndPreservesExistingHrChanges() {
        assertEquals(10_000, seeds.seedIfEmpty());
        jdbc.update("UPDATE employees SET full_name = 'Demo HR edit', salary_amount = 123456 WHERE employee_number = 'SYN-000001'");
        var before = jdbc.queryForList("SELECT * FROM employees ORDER BY id");
        assertEquals(0, seeds.seedIfEmpty());
        assertEquals(before, jdbc.queryForList("SELECT * FROM employees ORDER BY id"));
    }

    @Test
    void concurrentDemoSeedingCreatesOnlyOneCompleteDataset() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return seeds.seedIfEmpty(); });
            var second = executor.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return seeds.seedIfEmpty(); });
            start.countDown();
            assertEquals(List.of(0, 10_000), java.util.stream.Stream.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(10_000L, employeeCount());
    }

    @Test
    void refusesAnyExistingEmployeeEvenWhenItIsNotPartOfTheSyntheticDataset() {
        jdbc.update("""
                INSERT INTO employees (employee_number, full_name, department_id, country_code, currency_code, salary_amount)
                VALUES ('EXISTING', 'Existing Person', 1, 'US', 'USD', 100)
                """);
        assertThrows(IllegalStateException.class, seeds::seed);
        assertEquals(1L, employeeCount());
        assertEquals("Existing Person", jdbc.queryForObject("SELECT full_name FROM employees", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"department", "country", "currency"})
    void incompleteCatalogsFailBeforeAnyEmployeesAreWritten(String catalog) {
        switch (catalog) {
            case "department" -> jdbc.update("DELETE FROM departments WHERE name = 'Legal'");
            case "country" -> jdbc.update("DELETE FROM countries WHERE code = 'KW'");
            case "currency" -> jdbc.update("DELETE FROM currencies WHERE code = 'KWD'");
            default -> throw new AssertionError("Unexpected test catalog");
        }
        assertThrows(IllegalStateException.class, seeds::seed);
        assertEquals(0L, employeeCount());
    }

    @Test
    void readsCatalogIdsAndCurrencyPrecisionInsteadOfAssumingMigrationIds() {
        var departmentNames = jdbc.queryForList("SELECT name FROM departments ORDER BY id", String.class);
        jdbc.update("DELETE FROM departments");
        for (int index = 0; index < departmentNames.size(); index++) {
            jdbc.update("INSERT INTO departments (id, name) OVERRIDING SYSTEM VALUE VALUES (?, ?)",
                    101 + index, departmentNames.get(index));
        }
        jdbc.update("UPDATE currencies SET minor_units = 4 WHERE code = 'USD'");
        assertEquals(10_000, seeds.seed());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM employees WHERE department_id < 100", Long.class));
        assertTrue(jdbc.queryForObject("""
                SELECT count(*) FROM employees WHERE currency_code = 'USD' AND salary_amount <> round(salary_amount, 2)
                """, Long.class) > 0);
    }

    @Test
    void aLateBatchFailureRollsBackEarlierBatchesAndAFreshLoadIsReproducible() {
        jdbc.execute("ALTER TABLE employees ADD CONSTRAINT test_seed_late_failure CHECK (employee_number <> 'SYN-009999')");
        assertThrows(DataIntegrityViolationException.class, seeds::seed);
        assertEquals(0L, employeeCount());
        jdbc.execute("ALTER TABLE employees DROP CONSTRAINT test_seed_late_failure");
        seeds.seed();
        var first = businessRows();
        jdbc.update("DELETE FROM employees");
        seeds.seed();
        assertEquals(first, businessRows());
        assertEquals(10_000L, employeeCount());
    }

    @Test
    void concurrentSeedAttemptsAllowOnlyOneCompleteDataset() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> seedAfter(start));
            var second = executor.submit(() -> seedAfter(start));
            start.countDown();
            assertEquals(10_000, first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(10_000L, employeeCount());
    }

    @Test
    void packagedSeedCommandExitsWithoutAnHttpServerAndReturnsFailureOnRerun() throws Exception {
        CommandResult first = System.getProperty("os.name").startsWith("Windows")
                ? runPackagedCommand("--seed-employees")
                : runCommand(List.of("sh", Path.of("../scripts/seed-employees.sh").toAbsolutePath().toString()));
        assertEquals(0, first.exitCode(), first.output());
        assertTrue(first.output().contains("Seeded 10000 synthetic employees."));
        assertFalse(first.output().contains("Tomcat started"));
        assertEquals(10_000L, employeeCount());
        var before = businessRows();
        CommandResult repeated = runPackagedCommand("--seed-employees");
        assertTrue(repeated.exitCode() != 0, repeated.output());
        assertTrue(repeated.output().contains("empty employee table"));
        assertEquals(before, businessRows());
        CommandResult invalid = runPackagedCommand("--seed-employees", "--server.port=0");
        assertTrue(invalid.exitCode() != 0, invalid.output());
        assertEquals(before, businessRows());
    }

    private int seedAfter(CountDownLatch start) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            return seeds.seed();
        } catch (IllegalStateException existingData) {
            assertTrue(existingData.getMessage().contains("empty employee table"));
            return 0;
        }
    }

    private List<?> businessRows() {
        return jdbc.queryForList("""
                SELECT employee_number, full_name, department_id, country_code, currency_code, salary_amount
                FROM employees ORDER BY employee_number
                """);
    }

    private static long employeeCount() {
        return jdbc.queryForObject("SELECT count(*) FROM employees", Long.class);
    }

    private CommandResult runPackagedCommand(String... arguments) throws Exception {
        Path jar = Path.of("target/salary-management-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Run through Maven verify so the executable JAR exists");
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar.toString()));
        command.addAll(List.of(arguments));
        return runCommand(command);
    }

    private CommandResult runCommand(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().put("DB_URL", POSTGRES.getJdbcUrl());
        builder.environment().put("DB_USERNAME", POSTGRES.getUsername());
        builder.environment().put("DB_PASSWORD", POSTGRES.getPassword());
        builder.environment().put("APP_DEMO_SEED_ENABLED", "true");
        // A non-web seed command does not need an HR login account.
        builder.environment().remove("HR_USERNAME");
        builder.environment().remove("HR_PASSWORD_HASH");
        Path output = Files.createTempFile("salary-seed-command-", ".log");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Seed command must terminate");
            return new CommandResult(process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
