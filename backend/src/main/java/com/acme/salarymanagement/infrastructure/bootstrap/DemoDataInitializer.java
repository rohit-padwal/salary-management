package com.acme.salarymanagement.infrastructure.bootstrap;

import com.acme.salarymanagement.employee.seed.EmployeeSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true")
class DemoDataInitializer implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DemoDataInitializer.class);
    private final EmployeeSeedService seeds;

    DemoDataInitializer(EmployeeSeedService seeds) {
        this.seeds = seeds;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int inserted = seeds.seedIfEmpty();
        LOG.info("Demo initialization complete: inserted {} employees; existing data preserved.", inserted);
    }
}
