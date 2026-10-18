package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;

public record EmployeePageRequest(int page, int size, String q) {

    public EmployeePageRequest(int page, int size) {
        this(page, size, null);
    }

    public EmployeePageRequest {
        if (page < 0) {
            throw new InputValidationException("page", "OUT_OF_RANGE", "Page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw new InputValidationException("size", "OUT_OF_RANGE", "Size must be between 1 and 100.");
        }
        q = normalizeSearch(q);
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > 200) {
            throw new InputValidationException("q", "TOO_LONG", "Search must contain at most 200 characters.");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new InputValidationException("q", "INVALID_FORMAT", "Search cannot contain a null character.");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
