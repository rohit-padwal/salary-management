package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.referencedata.CountryRepository;
import com.acme.salarymanagement.referencedata.CurrencyRepository;
import com.acme.salarymanagement.referencedata.DepartmentRepository;
import com.acme.salarymanagement.validation.InputValidationException;
import org.springframework.stereotype.Component;

@Component
public class EmployeeCriteriaValidator {

    private final DepartmentRepository departments;
    private final CountryRepository countries;
    private final CurrencyRepository currencies;

    EmployeeCriteriaValidator(DepartmentRepository departments, CountryRepository countries, CurrencyRepository currencies) {
        this.departments = departments;
        this.countries = countries;
        this.currencies = currencies;
    }

    public void validate(EmployeeReadCriteria criteria) {
        if (criteria.departmentId() != null) {
            departments.findById(criteria.departmentId()).orElseThrow(() -> unknownReference("departmentId", "department"));
        }
        if (criteria.countryCode() != null) {
            countries.findById(criteria.countryCode()).orElseThrow(() -> unknownReference("countryCode", "country"));
        }
        if (criteria.currencyCode() != null) {
            currencies.findById(criteria.currencyCode()).orElseThrow(() -> unknownReference("currencyCode", "currency"));
        }
    }

    private static InputValidationException unknownReference(String field, String label) {
        return new InputValidationException(field, "UNKNOWN_REFERENCE", "Select a supported " + label + ".");
    }
}
