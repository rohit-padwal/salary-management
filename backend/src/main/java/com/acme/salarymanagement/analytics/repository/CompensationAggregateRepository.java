package com.acme.salarymanagement.analytics.repository;

import java.sql.Types;
import java.util.List;

import com.acme.salarymanagement.analytics.model.CompensationAggregate;
import com.acme.salarymanagement.analytics.model.CompensationGrouping;
import com.acme.salarymanagement.employee.model.EmployeeReadCriteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

@Repository
public class CompensationAggregateRepository {

    private final JdbcTemplate jdbc;

    public CompensationAggregateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompensationAggregate> aggregate(CompensationGrouping grouping, EmployeeReadCriteria criteria) {
        String key = switch (grouping) {
            case DEPARTMENT -> "dimension.id";
            case COUNTRY -> "dimension.code";
        };
        String dimensionJoin = switch (grouping) {
            case DEPARTMENT -> "JOIN departments dimension ON dimension.id = e.department_id";
            case COUNTRY -> "JOIN countries dimension ON dimension.code = e.country_code";
        };
        // Only enum-selected SQL fragments are interpolated; every user value is bound.
        String sql = """
                SELECT %s::text AS group_key, dimension.name AS group_label,
                       c.code AS currency_code, c.minor_units,
                       count(*) AS headcount, sum(e.salary_amount) AS total_salary,
                       min(e.salary_amount) AS min_salary, max(e.salary_amount) AS max_salary
                FROM employees e
                %s
                JOIN currencies c ON c.code = e.currency_code
                WHERE (? IS NULL OR (lower(e.employee_number) LIKE lower(?) ESCAPE '!'
                                     OR lower(e.full_name) LIKE lower(?) ESCAPE '!'))
                  AND (? IS NULL OR e.department_id = ?)
                  AND (? IS NULL OR e.country_code = ?)
                  AND (? IS NULL OR e.currency_code = ?)
                GROUP BY %s, dimension.name, c.code, c.minor_units
                ORDER BY %s ASC, c.code ASC
                """.formatted(key, dimensionJoin, key, key);
        SqlParameterValue pattern = new SqlParameterValue(Types.VARCHAR, criteria.likePattern());
        SqlParameterValue department = new SqlParameterValue(Types.BIGINT, criteria.departmentId());
        SqlParameterValue country = new SqlParameterValue(Types.VARCHAR, criteria.countryCode());
        SqlParameterValue currency = new SqlParameterValue(Types.VARCHAR, criteria.currencyCode());
        return jdbc.query(sql, (row, index) -> new CompensationAggregate(
                row.getString("group_key"), row.getString("group_label"), row.getString("currency_code"),
                row.getInt("minor_units"), row.getLong("headcount"), row.getBigDecimal("total_salary"),
                row.getBigDecimal("min_salary"), row.getBigDecimal("max_salary")),
                pattern, pattern, pattern, department, department, country, country, currency, currency);
    }
}
