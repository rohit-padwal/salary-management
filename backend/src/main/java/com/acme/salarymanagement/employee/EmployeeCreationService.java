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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeCreationService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final CountryRepository countries;
    private final CurrencyRepository currencies;

    EmployeeCreationService(EmployeeRepository employees, DepartmentRepository departments,
            CountryRepository countries, CurrencyRepository currencies) {
        this.employees = employees;
        this.departments = departments;
        this.countries = countries;
        this.currencies = currencies;
    }

    @Transactional
    public EmployeeResult create(CreateEmployeeCommand command) {
        EmployeeInput input = EmployeeInput.validate(command);
        Department department = departments.findById(input.departmentId())
                .orElseThrow(() -> unknownReference("departmentId", "Select a supported department."));
        Country country = countries.findById(input.countryCode())
                .orElseThrow(() -> unknownReference("countryCode", "Select a supported country."));
        Currency currency = currencies.findById(input.currencyCode())
                .orElseThrow(() -> unknownReference("currencyCode", "Select a supported currency."));
        BigDecimal salary = SalaryRules.validate(input.salaryAmount(), currency.getMinorUnits());

        try {
            // Flush inside this boundary so a concurrent unique-key violation is translated here.
            Employee employee = employees.saveAndFlush(new Employee(input, department, country, currency, salary));
            return employee.toResult();
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "23505".equals(constraint.getSQLState())
                        && "uq_employees_employee_number".equals(constraint.getConstraintName())) {
                    throw new EmployeeNumberConflictException(failure);
                }
            }
            throw failure;
        }
    }

    private static InputValidationException unknownReference(String field, String message) {
        return new InputValidationException(field, "UNKNOWN_REFERENCE", message);
    }
}
