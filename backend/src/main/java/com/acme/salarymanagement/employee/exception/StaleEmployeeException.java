package com.acme.salarymanagement.employee.exception;

public class StaleEmployeeException extends RuntimeException {

    public StaleEmployeeException() {
        super("Employee changed since it was loaded. Reload it before saving.");
    }
}
