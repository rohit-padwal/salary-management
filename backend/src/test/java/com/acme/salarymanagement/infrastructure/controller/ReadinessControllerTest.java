package com.acme.salarymanagement.infrastructure.controller;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadinessControllerTest {
    private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void staysUnavailableUntilApplicationRunnersHaveFinished() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        var response = new ReadinessController(availability, jdbc).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals(Map.of("status", "STARTING"), response.getBody());
        verifyNoInteractions(jdbc);
    }

    @Test
    void reportsReadyWhenStartupAndDatabaseAreReady() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        var response = new ReadinessController(availability, jdbc).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "UP"), response.getBody());
    }

    @Test
    void reportsDatabaseFailureWithoutLeakingConnectionDetails() {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("private connection detail"));
        var response = new ReadinessController(availability, jdbc).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals(Map.of("status", "DOWN"), response.getBody());
    }
}
