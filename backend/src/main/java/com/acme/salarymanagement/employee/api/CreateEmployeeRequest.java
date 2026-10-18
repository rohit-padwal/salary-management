package com.acme.salarymanagement.employee.api;

import com.acme.salarymanagement.api.PositiveId;
import com.acme.salarymanagement.employee.EmployeeDetails;
import jakarta.validation.constraints.NotBlank;

record CreateEmployeeRequest(
        @NotBlank String employeeNumber,
        @NotBlank String fullName,
        @NotBlank String departmentId,
        @NotBlank String countryCode,
        @NotBlank String salaryAmount,
        @NotBlank String currencyCode) {

    EmployeeDetails toCommand() {
        long id = PositiveId.parse(departmentId, "departmentId", "Department ID");
        return new EmployeeDetails(employeeNumber, fullName, id, countryCode, salaryAmount, currencyCode);
    }
}
