package com.acme.salarymanagement.employee.model;

import java.math.BigDecimal;

import com.acme.salarymanagement.employee.validation.EmployeeInput;
import com.acme.salarymanagement.referencedata.entity.Country;
import com.acme.salarymanagement.referencedata.entity.Currency;
import com.acme.salarymanagement.referencedata.entity.Department;

public record ResolvedEmployeeData(EmployeeInput input, Department department, Country country,
        Currency currency, BigDecimal salary) {
}
