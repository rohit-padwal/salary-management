package com.acme.salarymanagement.api;

import com.acme.salarymanagement.validation.InputValidationException;

public final class PositiveId {

    private PositiveId() {
    }

    public static long parse(String value, String field, String label) {
        if (value == null || value.isBlank()) {
            throw new InputValidationException(field, "REQUIRED", label + " is required.");
        }
        if (!value.matches("[0-9]+")) {
            throw new InputValidationException(field, "INVALID_FORMAT", label + " must be a positive integer string.");
        }
        long id;
        try {
            id = Long.parseLong(value);
        } catch (NumberFormatException outOfRange) {
            throw new InputValidationException(field, "OUT_OF_RANGE", label + " exceeds the supported range.");
        }
        if (id <= 0) {
            throw new InputValidationException(field, "MUST_BE_POSITIVE", label + " must be greater than zero.");
        }
        return id;
    }
}
