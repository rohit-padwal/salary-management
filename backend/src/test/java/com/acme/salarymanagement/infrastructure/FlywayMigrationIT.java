package com.acme.salarymanagement.infrastructure;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    private static Flyway flyway;
    private static MigrateResult initialMigration;
    private Connection connection;

    @BeforeAll
    static void migrateFreshDatabase() {
        POSTGRES.start();
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
        initialMigration = flyway.migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void beginTransaction() throws SQLException {
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollBackChanges() throws SQLException {
        if (connection != null) {
            try (Connection current = connection) {
                current.rollback();
            }
        }
    }

    @Test
    void migratesEmptyDatabaseAndDoesNotRepeatCompletedMigrations() {
        assertEquals(2, initialMigration.migrationsExecuted);
        assertEquals(2, flyway.info().applied().length);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }

    @Test
    void initializesOnlyApprovedReferenceData() throws SQLException {
        assertEquals(8, count("departments"));
        assertEquals(6, count("countries"));
        assertEquals(6, count("currencies"));
        assertEquals(0, count("employees"));

        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT code, minor_units FROM currencies")) {
            Map<String, Integer> expected = Map.of(
                    "INR", 2, "USD", 2, "GBP", 2, "EUR", 2, "JPY", 0, "KWD", 3);
            int matches = 0;
            while (rows.next()) {
                assertEquals(expected.get(rows.getString("code")), rows.getInt("minor_units"));
                matches++;
            }
            assertEquals(expected.size(), matches);
        }
    }

    @Test
    void storesExactCurrentSalaryAndAllowsIndependentCountryAndCurrency() throws SQLException {
        long id = insertEmployee("EMP-000001", "Asha Rao", "IN", "125000.12", "USD");

        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM employees WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertTrue(id > 0);
                assertEquals(0, new BigDecimal("125000.12").compareTo(row.getBigDecimal("salary_amount")));
                assertEquals("IN", row.getString("country_code"));
                assertEquals("USD", row.getString("currency_code"));
                assertEquals(0, row.getLong("version"));
                assertNotNull(row.getTimestamp("created_at"));
                assertNotNull(row.getTimestamp("updated_at"));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "employee_number", "full_name", "department_id", "country_code", "salary_amount",
            "currency_code", "version", "created_at", "updated_at"
    })
    void rejectsMissingRequiredEmployeeValues(String column) throws SQLException {
        insertValidEmployee();
        assertSqlState("23502", () -> execute("UPDATE employees SET " + column + " = NULL"));
    }

    @ParameterizedTest
    @CsvSource({"0,23514", "-0.01,23514", "NaN,23514", "Infinity,22003", "-Infinity,22003", "1000000000000000,22003"})
    void rejectsInvalidSalaryValues(String amount, String sqlState) {
        assertSqlState(sqlState, () -> insertEmployee("EMP-000001", "Asha Rao", "IN", amount, "INR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"emp-1", " EMP-1", "-EMP-1", "EMP!1"})
    void rejectsNonCanonicalEmployeeNumbers(String employeeNumber) {
        assertSqlState("23514", () -> insertEmployee(employeeNumber, "Asha Rao", "IN", "1000", "INR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsBlankEmployeeNames(String name) {
        assertSqlState("23514", () -> insertEmployee("EMP-000001", name, "IN", "1000", "INR"));
    }

    @ParameterizedTest
    @CsvSource({"employee_number,33", "full_name,201"})
    void rejectsEmployeeTextBeyondStorageLimits(String column, int length) throws SQLException {
        insertValidEmployee();
        assertSqlState("22001", () -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE employees SET " + column + " = ?")) {
                statement.setString(1, "A".repeat(length));
                statement.executeUpdate();
            }
        });
    }

    @Test
    void rejectsDuplicateEmployeeNumbers() throws SQLException {
        insertValidEmployee();
        assertSqlState("23505", this::insertValidEmployee);
    }

    @Test
    void rejectsDepartmentNamesThatDifferOnlyByCase() {
        assertSqlState("23505", () -> execute("INSERT INTO departments (name) VALUES ('engineering')"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departments", "countries", "currencies"})
    void rejectsBlankReferenceNames(String table) {
        assertSqlState("23514", () -> execute("UPDATE " + table + " SET name = '   '"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"departments", "countries", "currencies"})
    void preventsDeletingReferencedLookupRecords(String table) throws SQLException {
        insertValidEmployee();
        assertSqlState("23001", () -> execute("DELETE FROM " + table));
    }

    @ParameterizedTest
    @CsvSource({"country_code,ZZ", "currency_code,ZZZ", "department_id,9223372036854775807"})
    void rejectsUnknownReferences(String column, String value) throws SQLException {
        insertValidEmployee();
        assertSqlState("23503", () -> execute("UPDATE employees SET " + column + " = '" + value + "'"));
    }

    @ParameterizedTest
    @CsvSource({"countries,in", "currencies,usd"})
    void rejectsNonCanonicalReferenceCodes(String table, String code) {
        assertSqlState("23514", () -> execute("UPDATE " + table + " SET code = '" + code + "'"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 5})
    void rejectsUnsupportedMinorUnitCounts(int minorUnits) {
        assertSqlState("23514", () -> execute("UPDATE currencies SET minor_units = " + minorUnits));
    }

    @Test
    void rejectsNegativeVersions() throws SQLException {
        insertValidEmployee();
        assertSqlState("23514", () -> execute("UPDATE employees SET version = -1"));
    }

    private long insertValidEmployee() throws SQLException {
        return insertEmployee("EMP-000001", "Asha Rao", "IN", "1000.00", "INR");
    }

    private long insertEmployee(String number, String name, String country, String salary, String currency)
            throws SQLException {
        String sql = """
                INSERT INTO employees (employee_number, full_name, department_id, country_code, salary_amount, currency_code)
                VALUES (?, ?, (SELECT id FROM departments WHERE name = 'Engineering'), ?, CAST(? AS NUMERIC), ?)
                RETURNING id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, number);
            statement.setString(2, name);
            statement.setString(3, country);
            statement.setString(4, salary);
            statement.setString(5, currency);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private long count(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void assertSqlState(String expected, SqlAction action) {
        SQLException exception = assertThrows(SQLException.class, action::execute);
        assertEquals(expected, exception.getSQLState());
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute() throws SQLException;
    }
}
