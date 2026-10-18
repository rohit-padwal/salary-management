package com.acme.salarymanagement.employee.dto;

import java.util.List;

import com.acme.salarymanagement.employee.model.EmployeePageResult;

public record EmployeePageResponse(
        List<EmployeeResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    public static EmployeePageResponse from(EmployeePageResult result) {
        return new EmployeePageResponse(result.items().stream().map(EmployeeResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
