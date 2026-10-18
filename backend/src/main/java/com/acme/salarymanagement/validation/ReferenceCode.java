package com.acme.salarymanagement.validation;

import java.util.Locale;

public final class ReferenceCode {

    private ReferenceCode() {
    }

    public static String normalize(String value, String field, int length) {
        if (value == null || value.isBlank()) {
            throw new InputValidationException(field, "REQUIRED", "This field is required.");
        }
        String code = value.strip();
        // Check ASCII before case conversion so Unicode expansion cannot create a valid code.
        if (!code.matches("[A-Za-z]{" + length + "}")) {
            throw new InputValidationException(field, "INVALID_FORMAT", "Code must contain " + length + " ASCII letters.");
        }
        return code.toUpperCase(Locale.ROOT);
    }
}
