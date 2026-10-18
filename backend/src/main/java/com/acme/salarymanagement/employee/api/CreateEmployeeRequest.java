package com.acme.salarymanagement.employee.api;

import com.acme.salarymanagement.api.PositiveId;
import com.acme.salarymanagement.employee.CreateEmployeeCommand;
import jakarta.validation.constraints.NotBlank;

record CreateEmployeeRequest(
        @NotBlank String employeeNumber,
        @NotBlank String fullName,
        @NotBlank String departmentId,
        @NotBlank String countryCode,
        @NotBlank String salaryAmount,
        @NotBlank String currencyCode) {

    CreateEmployeeCommand toCommand() {
        long id = PositiveId.parse(departmentId, "departmentId", "Department ID");
        return new CreateEmployeeCommand(employeeNumber, fullName, id, countryCode, salaryAmount, currencyCode);
    }
}
