package com.acme.salarymanagement.employee.dto;

import com.acme.salarymanagement.api.validation.PositiveId;
import com.acme.salarymanagement.employee.model.EmployeeDetails;
import com.acme.salarymanagement.employee.model.UpdateEmployeeCommand;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.validation.constraints.NotBlank;

public record UpdateEmployeeRequest(
        @NotBlank String employeeNumber,
        @NotBlank String fullName,
        @NotBlank String departmentId,
        @NotBlank String countryCode,
        @NotBlank String salaryAmount,
        @NotBlank String currencyCode,
        @NotBlank String version) {

    public UpdateEmployeeCommand toCommand() {
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
