package com.acme.salarymanagement.employee.service;

import java.math.BigDecimal;

import com.acme.salarymanagement.compensation.SalaryRules;
import com.acme.salarymanagement.employee.model.EmployeeDetails;
import com.acme.salarymanagement.employee.model.ResolvedEmployeeData;
import com.acme.salarymanagement.employee.validation.EmployeeInput;
import com.acme.salarymanagement.referencedata.entity.Country;
import com.acme.salarymanagement.referencedata.entity.Currency;
import com.acme.salarymanagement.referencedata.entity.Department;
import com.acme.salarymanagement.referencedata.repository.CountryRepository;
import com.acme.salarymanagement.referencedata.repository.CurrencyRepository;
import com.acme.salarymanagement.referencedata.repository.DepartmentRepository;
import com.acme.salarymanagement.validation.InputValidationException;
import org.springframework.stereotype.Component;

@Component
class EmployeeDataResolver {

    private final DepartmentRepository departments;
    private final CountryRepository countries;
    private final CurrencyRepository currencies;

    EmployeeDataResolver(DepartmentRepository departments, CountryRepository countries, CurrencyRepository currencies) {
        this.departments = departments;
        this.countries = countries;
        this.currencies = currencies;
    }

    ResolvedEmployeeData resolve(EmployeeDetails details) {
        EmployeeInput input = EmployeeInput.validate(details);
        Department department = departments.findById(input.departmentId())
                .orElseThrow(() -> unknownReference("departmentId", "Select a supported department."));
        Country country = countries.findById(input.countryCode())
                .orElseThrow(() -> unknownReference("countryCode", "Select a supported country."));
        Currency currency = currencies.findById(input.currencyCode())
                .orElseThrow(() -> unknownReference("currencyCode", "Select a supported currency."));
        BigDecimal salary = SalaryRules.validate(input.salaryAmount(), currency.getMinorUnits());
        return new ResolvedEmployeeData(input, department, country, currency, salary);
    }

    private InputValidationException unknownReference(String field, String message) {
        return new InputValidationException(field, "UNKNOWN_REFERENCE", message);
    }
}
