package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.infrastructure.DatabaseClock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeCreationService {

    private final EmployeeRepository employees;
    private final EmployeeDataResolver data;
    private final DatabaseClock clock;

    EmployeeCreationService(EmployeeRepository employees, EmployeeDataResolver data, DatabaseClock clock) {
        this.employees = employees;
        this.data = data;
        this.clock = clock;
    }

    @Transactional
    public EmployeeResult create(EmployeeDetails command) {
        ResolvedEmployeeData resolved = data.resolve(command);

        try {
            // Flush inside this boundary so a concurrent unique-key violation is translated here.
            Employee employee = employees.saveAndFlush(new Employee(resolved, clock.now()));
            return employee.toResult();
        } catch (DataIntegrityViolationException failure) {
            throw EmployeeWriteConflicts.translate(failure);
        }
    }
}
