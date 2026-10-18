package com.acme.salarymanagement.employee.service;

import com.acme.salarymanagement.employee.exception.EmployeeNumberConflictException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class EmployeeWriteConflicts {

    private EmployeeWriteConflicts() {
    }

    static RuntimeException translate(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())
                    && "uq_employees_employee_number".equals(constraint.getConstraintName())) {
                return new EmployeeNumberConflictException(failure);
            }
        }
        return failure;
    }
}
