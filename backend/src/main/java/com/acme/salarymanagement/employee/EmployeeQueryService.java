package com.acme.salarymanagement.employee;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeQueryService {

    private final EmployeeRepository employees;

    EmployeeQueryService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public EmployeeResult getById(long id) {
        return employees.findById(id).map(Employee::toResult).orElseThrow(EmployeeNotFoundException::new);
    }
}
