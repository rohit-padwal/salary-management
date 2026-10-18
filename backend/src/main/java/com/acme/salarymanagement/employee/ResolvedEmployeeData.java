package com.acme.salarymanagement.employee;

import java.math.BigDecimal;

import com.acme.salarymanagement.referencedata.Country;
import com.acme.salarymanagement.referencedata.Currency;
import com.acme.salarymanagement.referencedata.Department;

record ResolvedEmployeeData(EmployeeInput input, Department department, Country country,
        Currency currency, BigDecimal salary) {
}
