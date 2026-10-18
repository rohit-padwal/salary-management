package com.acme.salarymanagement.employee;

public record CreateEmployeeCommand(
        String employeeNumber,
        String fullName,
        Long departmentId,
        String countryCode,
        String salaryAmount,
        String currencyCode) {
}
