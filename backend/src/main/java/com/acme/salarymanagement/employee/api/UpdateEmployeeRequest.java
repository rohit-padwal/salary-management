package com.acme.salarymanagement.employee.api;

import com.acme.salarymanagement.api.PositiveId;
import com.acme.salarymanagement.employee.EmployeeDetails;
import com.acme.salarymanagement.employee.UpdateEmployeeCommand;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.validation.constraints.NotBlank;

record UpdateEmployeeRequest(
        @NotBlank String employeeNumber,
        @NotBlank String fullName,
        @NotBlank String departmentId,
        @NotBlank String countryCode,
        @NotBlank String salaryAmount,
        @NotBlank String currencyCode,
        @NotBlank String version) {

    UpdateEmployeeCommand toCommand() {
        long department = PositiveId.parse(departmentId, "departmentId", "Department ID");
        return new UpdateEmployeeCommand(new EmployeeDetails(employeeNumber, fullName, department,
                countryCode, salaryAmount, currencyCode), originalVersion());
    }

    private long originalVersion() {
        if (!version.matches("[0-9]+")) {
            throw new InputValidationException("version", "INVALID_FORMAT", "Version must be a nonnegative integer string.");
        }
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException outOfRange) {
            throw new InputValidationException("version", "OUT_OF_RANGE", "Version exceeds the supported range.");
        }
    }
}
