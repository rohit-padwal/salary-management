package com.acme.salarymanagement.employee.dto;

import com.acme.salarymanagement.api.validation.PositiveId;
import com.acme.salarymanagement.employee.model.EmployeeDetails;
import jakarta.validation.constraints.NotBlank;

public record CreateEmployeeRequest(
        @NotBlank String employeeNumber,
        @NotBlank String fullName,
        @NotBlank String departmentId,
        @NotBlank String countryCode,
        @NotBlank String salaryAmount,
        @NotBlank String currencyCode) {

    public EmployeeDetails toCommand() {
        long id = PositiveId.parse(departmentId, "departmentId", "Department ID");
        return new EmployeeDetails(employeeNumber, fullName, id, countryCode, salaryAmount, currencyCode);
    }
}
