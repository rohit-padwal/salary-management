package com.acme.salarymanagement.employee.exception;

public class EmployeeNumberConflictException extends RuntimeException {

    public EmployeeNumberConflictException(Throwable cause) {
        super("Employee number is already in use.", cause);
    }
}
