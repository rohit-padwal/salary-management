package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;

public record EmployeePageRequest(int page, int size) {

    public EmployeePageRequest {
        if (page < 0) {
            throw new InputValidationException("page", "OUT_OF_RANGE", "Page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw new InputValidationException("size", "OUT_OF_RANGE", "Size must be between 1 and 100.");
        }
    }
}
