package com.acme.salarymanagement.employee.exception;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException() {
        super("Employee was not found.");
    }
}
