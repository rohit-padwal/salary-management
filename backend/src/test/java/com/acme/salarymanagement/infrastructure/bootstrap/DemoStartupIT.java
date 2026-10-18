package com.acme.salarymanagement.infrastructure.bootstrap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.acme.salarymanagement.SalaryManagementApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoStartupIT {
    @Test
    void optInWebStartupSeedsOnceAndRestartPreservesHrChanges() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:18.6")) {
            postgres.start();
            String passwordHash = new BCryptPasswordEncoder(10).encode("synthetic-demo-test");
            try (var first = start(postgres, passwordHash)) {
                JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
                assertEquals(10_000L, jdbc.queryForObject("SELECT count(*) FROM employees", Long.class));
                assertReady(first);
                jdbc.update("UPDATE employees SET full_name = 'Preserved demo edit' WHERE employee_number = 'SYN-000001'");
            }
            try (var restarted = start(postgres, passwordHash)) {
                JdbcTemplate jdbc = restarted.getBean(JdbcTemplate.class);
                assertEquals(10_000L, jdbc.queryForObject("SELECT count(*) FROM employees", Long.class));
                assertEquals("Preserved demo edit", jdbc.queryForObject("SELECT full_name FROM employees WHERE employee_number = 'SYN-000001'", String.class));
                assertReady(restarted);
            }
        }
    }

    private ConfigurableApplicationContext start(PostgreSQLContainer postgres, String passwordHash) {
        return new SpringApplicationBuilder(SalaryManagementApplication.class).profiles("local")
                .run("--server.port=0", "--server.address=127.0.0.1", "--debug=false",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--app.security.hr.username=demo-test", "--app.security.hr.password-hash=" + passwordHash,
                        "--app.demo.seed-enabled=true");
    }

    private void assertReady(ConfigurableApplicationContext context) throws Exception {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("{\"status\":\"UP\"}", response.body());
        }
    }
}
