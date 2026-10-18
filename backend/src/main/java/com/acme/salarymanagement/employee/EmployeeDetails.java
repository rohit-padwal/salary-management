package com.acme.salarymanagement.employee;

public record EmployeeDetails(
        String employeeNumber,
        String fullName,
        Long departmentId,
        String countryCode,
        String salaryAmount,
        String currencyCode) {
}
