package com.acme.salarymanagement.employee.dto;
import com.acme.salarymanagement.employee.model.EmployeeResult;

public record EmployeeResponse(
        String id,
        String employeeNumber,
        String fullName,
        String departmentId,
        String departmentName,
        String countryCode,
        String countryName,
        String salaryAmount,
        String currencyCode,
        String version,
        String createdAt,
        String updatedAt) {

    public static EmployeeResponse from(EmployeeResult employee) {
        return new EmployeeResponse(employee.id().toString(), employee.employeeNumber(), employee.fullName(),
                employee.departmentId().toString(), employee.departmentName(), employee.countryCode(),
                employee.countryName(), employee.salaryAmount().toPlainString(), employee.currencyCode(),
                employee.version().toString(), employee.createdAt().toString(), employee.updatedAt().toString());
    }
}
