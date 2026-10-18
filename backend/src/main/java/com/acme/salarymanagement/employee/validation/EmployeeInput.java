package com.acme.salarymanagement.employee.validation;

import java.util.Locale;

import com.acme.salarymanagement.employee.model.EmployeeDetails;
import com.acme.salarymanagement.validation.InputValidationException;
import com.acme.salarymanagement.validation.ReferenceCode;

public record EmployeeInput(String employeeNumber, String fullName, long departmentId,
        String countryCode, String salaryAmount, String currencyCode) {

    public static EmployeeInput validate(EmployeeDetails command) {
        String number = required(command.employeeNumber(), "employeeNumber");
        // Validate ASCII before uppercasing: Unicode case expansion must not create an accepted identifier.
        if (!number.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,31}")) {
            throw invalid("employeeNumber", "INVALID_FORMAT", "Employee number must contain 1-32 ASCII letters, digits, hyphens or underscores, starting with a letter or digit.");
        }
        String name = required(command.fullName(), "fullName");
        if (name.codePointCount(0, name.length()) > 200) {
            throw invalid("fullName", "TOO_LONG", "Full name must contain at most 200 characters.");
        }
        if (name.indexOf('\0') >= 0) {
            throw invalid("fullName", "INVALID_FORMAT", "Full name cannot contain a null character.");
        }
        if (command.departmentId() == null || command.departmentId() <= 0) {
            throw invalid("departmentId", "MUST_BE_POSITIVE", "Select a supported department.");
        }
        String country = ReferenceCode.normalize(command.countryCode(), "countryCode", 2);
        String currency = ReferenceCode.normalize(command.currencyCode(), "currencyCode", 3);
        return new EmployeeInput(number.toUpperCase(Locale.ROOT), name, command.departmentId(),
                country, command.salaryAmount(), currency);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "REQUIRED", "This field is required.");
        }
        return value.strip();
    }

    private static InputValidationException invalid(String field, String code, String message) {
        return new InputValidationException(field, code, message);
    }
}
