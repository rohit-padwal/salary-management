package com.acme.salarymanagement.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseClock {

    private final JdbcTemplate jdbc;

    public DatabaseClock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Instant now() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",
                (row, index) -> row.getObject(1, OffsetDateTime.class).toInstant());
    }
}
