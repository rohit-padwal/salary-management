package com.acme.salarymanagement.employee.service;

import com.acme.salarymanagement.employee.entity.Employee;
import com.acme.salarymanagement.employee.exception.EmployeeNotFoundException;
import com.acme.salarymanagement.employee.model.EmployeeResult;
import com.acme.salarymanagement.employee.model.ResolvedEmployeeData;
import com.acme.salarymanagement.employee.model.UpdateEmployeeCommand;
import com.acme.salarymanagement.employee.repository.EmployeeRepository;
import com.acme.salarymanagement.infrastructure.persistence.DatabaseClock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeUpdateService {

    private final EmployeeRepository employees;
    private final EmployeeDataResolver data;
    private final DatabaseClock clock;

    EmployeeUpdateService(EmployeeRepository employees, EmployeeDataResolver data, DatabaseClock clock) {
        this.employees = employees;
        this.data = data;
        this.clock = clock;
    }

    @Transactional
    public EmployeeResult update(long id, UpdateEmployeeCommand command) {
        Employee employee = employees.findForEditById(id).orElseThrow(EmployeeNotFoundException::new);
        employee.requireVersion(command.version());
        ResolvedEmployeeData replacement = data.resolve(command.details());
        if (!employee.hasSameDetails(replacement)) {
            employee.replaceWith(replacement, clock.now());
        }
        try {
            employees.flush();
        } catch (DataIntegrityViolationException failure) {
            throw EmployeeWriteConflicts.translate(failure);
        }
        return employee.toResult();
    }
}
