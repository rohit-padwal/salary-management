package com.acme.salarymanagement.employee;

public class StaleEmployeeException extends RuntimeException {

    public StaleEmployeeException() {
        super("Employee changed since it was loaded. Reload it before saving.");
    }
}
