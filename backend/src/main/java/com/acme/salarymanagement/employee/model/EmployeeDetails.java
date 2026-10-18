package com.acme.salarymanagement.employee.model;

public record EmployeeDetails(
        String employeeNumber,
        String fullName,
        Long departmentId,
        String countryCode,
        String salaryAmount,
        String currencyCode) {
}
