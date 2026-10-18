package com.acme.salarymanagement.employee;

public record UpdateEmployeeCommand(EmployeeDetails details, long version) {
}
