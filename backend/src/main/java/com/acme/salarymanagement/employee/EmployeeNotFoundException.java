package com.acme.salarymanagement.employee;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException() {
        super("Employee was not found.");
    }
}
