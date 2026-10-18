package com.acme.salarymanagement;

import java.util.Arrays;

import com.acme.salarymanagement.employee.seed.EmployeeSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SalaryManagementApplication {

	private static final Logger LOG = LoggerFactory.getLogger(SalaryManagementApplication.class);

	public static void main(String[] args) {
		if (Arrays.stream(args).anyMatch(argument -> argument.startsWith("--seed-employees"))) {
			if (args.length != 1 || !args[0].equals("--seed-employees")) {
				throw new IllegalArgumentException("Use --seed-employees alone; configure the database through environment variables.");
			}
			SpringApplication application = new SpringApplication(SalaryManagementApplication.class);
			application.setWebApplicationType(WebApplicationType.NONE);
			try (var context = application.run("--spring.main.web-application-type=none")) {
				int count = context.getBean(EmployeeSeedService.class).seed();
				LOG.info("Seeded {} synthetic employees.", count);
			}
			return;
		}
		SpringApplication.run(SalaryManagementApplication.class, args);
	}

}
