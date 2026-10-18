package com.acme.salarymanagement.security.config;

import com.acme.salarymanagement.security.exception.SecurityProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    private final WebApplicationContextRunner application = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfiguration.class, SecurityProblemWriter.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void webApplicationCannotStartWithoutAnExplicitHrAccount() {
        application.run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseMessage("Configure HR_USERNAME explicitly."));
    }

    @Test
    void webApplicationCannotStartWithPlaintextPasswordConfiguration() {
        application.withPropertyValues("app.security.hr.username=hr", "app.security.hr.password-hash=not-a-hash")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Configure HR_PASSWORD_HASH as a BCrypt hash with cost at least 10."));
    }
}
