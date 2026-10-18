package com.acme.salarymanagement.employee;

import java.util.List;

import com.acme.salarymanagement.referencedata.CountryRepository;
import com.acme.salarymanagement.referencedata.CurrencyRepository;
import com.acme.salarymanagement.referencedata.DepartmentRepository;
import com.acme.salarymanagement.validation.InputValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeQueryService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final CountryRepository countries;
    private final CurrencyRepository currencies;

    EmployeeQueryService(EmployeeRepository employees, DepartmentRepository departments,
            CountryRepository countries, CurrencyRepository currencies) {
        this.employees = employees;
        this.departments = departments;
        this.countries = countries;
        this.currencies = currencies;
    }

    @Transactional(readOnly = true)
    public EmployeeResult getById(long id) {
        return employees.findById(id).map(Employee::toResult).orElseThrow(EmployeeNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public EmployeePageResult list(EmployeePageRequest request) {
        validateFilters(request);
        String pattern = literalPattern(request.q());
        boolean hasCriteria = pattern != null || request.departmentId() != null
                || request.countryCode() != null || request.currencyCode() != null;
        long total = hasCriteria
                ? employees.countMatching(pattern, request.departmentId(), request.countryCode(), request.currencyCode())
                : employees.count();
        long totalPages = total / request.size() + (total % request.size() == 0 ? 0 : 1);
        long offset = (long) request.page() * request.size();
        // Skip empty pages before asking JPA to represent an unnecessarily large offset.
        List<EmployeeResult> items = List.of();
        if (offset < total) {
            PageRequest page = PageRequest.of(request.page(), request.size(), ordering(request.sort()));
            List<Employee> matches = hasCriteria
                    ? employees.findMatching(pattern, request.departmentId(), request.countryCode(), request.currencyCode(), page)
                    : employees.findAllBy(page);
            items = matches.stream().map(Employee::toResult).toList();
        }
        return new EmployeePageResult(items, request.page(), request.size(), total, totalPages);
    }

    private static Sort ordering(EmployeeSort sort) {
        String property = switch (sort.field()) {
            case EMPLOYEE_NUMBER -> "employeeNumber";
            case FULL_NAME -> "fullName";
            case DEPARTMENT_NAME -> "department.name";
            case COUNTRY_CODE -> "country.code";
            case CURRENCY_CODE -> "currency.code";
            case SALARY_AMOUNT -> "salaryAmount";
        };
        Sort.Direction direction = sort.direction() == EmployeeSort.Direction.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by("id"));
    }

    private void validateFilters(EmployeePageRequest request) {
        if (request.departmentId() != null) {
            departments.findById(request.departmentId()).orElseThrow(() -> unknownReference("departmentId", "department"));
        }
        if (request.countryCode() != null) {
            countries.findById(request.countryCode()).orElseThrow(() -> unknownReference("countryCode", "country"));
        }
        if (request.currencyCode() != null) {
            currencies.findById(request.currencyCode()).orElseThrow(() -> unknownReference("currencyCode", "currency"));
        }
    }

    private static InputValidationException unknownReference(String field, String label) {
        return new InputValidationException(field, "UNKNOWN_REFERENCE", "Select a supported " + label + ".");
    }

    private static String literalPattern(String query) {
        if (query == null) {
            return null;
        }
        // Escape the escape character first so later wildcard escapes remain intact.
        return "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
