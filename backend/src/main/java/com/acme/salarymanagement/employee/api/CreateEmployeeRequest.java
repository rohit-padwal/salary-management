package com.acme.salarymanagement.employee.api;

import com.acme.salarymanagement.employee.CreateEmployeeCommand;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.validation.constraints.NotBlank;

record CreateEmployeeRequest(
        @NotBlank String employeeNumber,
        @NotBlank String fullName,
        @NotBlank String departmentId,
        @NotBlank String countryCode,
        @NotBlank String salaryAmount,
        @NotBlank String currencyCode) {

    CreateEmployeeCommand toCommand() {
        if (!departmentId.matches("[0-9]+")) {
            throw new InputValidationException("departmentId", "INVALID_FORMAT", "Department ID must be a positive integer string.");
        }
        long id;
        try {
            id = Long.parseLong(departmentId);
        } catch (NumberFormatException outOfRange) {
            throw new InputValidationException("departmentId", "OUT_OF_RANGE", "Department ID exceeds the supported range.");
        }
        if (id <= 0) {
            throw new InputValidationException("departmentId", "MUST_BE_POSITIVE", "Department ID must be greater than zero.");
        }
        return new CreateEmployeeCommand(employeeNumber, fullName, id, countryCode, salaryAmount, currencyCode);
    }
}
