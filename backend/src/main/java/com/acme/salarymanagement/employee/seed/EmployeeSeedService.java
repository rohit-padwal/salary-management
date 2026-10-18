package com.acme.salarymanagement.employee.seed;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.acme.salarymanagement.compensation.SalaryRules;
import com.acme.salarymanagement.employee.validation.EmployeeInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeSeedService {

    private final JdbcTemplate jdbc;

    EmployeeSeedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int seed() {
        // Serialize seed attempts and block employee writes until the empty check/load commits.
        jdbc.execute("LOCK TABLE employees IN SHARE ROW EXCLUSIVE MODE");
        if (jdbc.queryForObject("SELECT count(*) FROM employees", Long.class) != 0) {
            throw new IllegalStateException("Seed requires an empty employee table; existing records were left unchanged.");
        }
        Map<String, Long> departments = new HashMap<>();
        jdbc.query("SELECT name, id FROM departments", row -> {
            departments.put(row.getString("name"), row.getLong("id"));
        });
        Map<String, Integer> precision = new HashMap<>();
        jdbc.query("SELECT code, minor_units FROM currencies", row -> {
            precision.put(row.getString("code"), row.getInt("minor_units"));
        });
        Set<String> countries = Set.copyOf(jdbc.queryForList("SELECT code FROM countries", String.class));
        // Validate the whole fixed-size dataset before any insert can reach numeric column coercion.
        List<SeedRow> rows = EmployeeSeedGenerator.generate(departments, precision).stream().map(details -> {
            EmployeeInput input = EmployeeInput.validate(details);
            if (!countries.contains(input.countryCode())) {
                throw new IllegalStateException("Seed requires the configured country: " + input.countryCode());
            }
            BigDecimal salary = SalaryRules.validate(input.salaryAmount(), precision.get(input.currencyCode()));
            return new SeedRow(input, salary);
        }).toList();
        jdbc.batchUpdate("""
                INSERT INTO employees (employee_number, full_name, department_id, country_code, currency_code, salary_amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """, rows, 500, (statement, row) -> {
            EmployeeInput input = row.input();
            statement.setString(1, input.employeeNumber());
            statement.setString(2, input.fullName());
            statement.setLong(3, input.departmentId());
            statement.setString(4, input.countryCode());
            statement.setString(5, input.currencyCode());
            statement.setBigDecimal(6, row.salary());
        });
        return rows.size();
    }

    private record SeedRow(EmployeeInput input, BigDecimal salary) {
    }
}
