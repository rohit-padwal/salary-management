package com.acme.salarymanagement.employee;

import java.math.BigDecimal;

import com.acme.salarymanagement.compensation.SalaryRules;
import com.acme.salarymanagement.referencedata.Country;
import com.acme.salarymanagement.referencedata.CountryRepository;
import com.acme.salarymanagement.referencedata.Currency;
import com.acme.salarymanagement.referencedata.CurrencyRepository;
import com.acme.salarymanagement.referencedata.Department;
import com.acme.salarymanagement.referencedata.DepartmentRepository;
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
