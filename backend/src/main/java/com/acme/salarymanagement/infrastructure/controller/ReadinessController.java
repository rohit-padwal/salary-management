package com.acme.salarymanagement.infrastructure.controller;

import java.util.Map;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReadinessController {
    private final ApplicationAvailability availability;
    private final JdbcTemplate jdbc;

    ReadinessController(ApplicationAvailability availability, JdbcTemplate jdbc) {
        this.availability = availability;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/health")
    ResponseEntity<Map<String, String>> health() {
        // Spring accepts traffic only after startup runners, including demo seeding, finish.
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(503).body(Map.of("status", "STARTING"));
        }
        try {
            if (Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class))) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
        } catch (DataAccessException unavailable) {
            // Public readiness exposes no database, credential, or exception details.
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
