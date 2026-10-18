package com.acme.salarymanagement.employee;

import java.util.List;

public record EmployeePageResult(
        List<EmployeeResult> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {
}
