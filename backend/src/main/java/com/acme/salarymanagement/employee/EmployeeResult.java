package com.acme.salarymanagement.employee;

import java.math.BigDecimal;
import java.time.Instant;

public record EmployeeResult(
        Long id,
        String employeeNumber,
        String fullName,
        Long departmentId,
        String departmentName,
        String countryCode,
        String countryName,
        BigDecimal salaryAmount,
        String currencyCode,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
