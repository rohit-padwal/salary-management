package com.acme.salarymanagement.employee.model;

public record UpdateEmployeeCommand(EmployeeDetails details, long version) {
}
