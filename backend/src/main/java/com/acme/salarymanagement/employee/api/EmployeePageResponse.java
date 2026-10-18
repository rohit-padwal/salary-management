package com.acme.salarymanagement.employee.api;

import java.util.List;

import com.acme.salarymanagement.employee.EmployeePageResult;

record EmployeePageResponse(
        List<EmployeeResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    static EmployeePageResponse from(EmployeePageResult result) {
        return new EmployeePageResponse(result.items().stream().map(EmployeeResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
