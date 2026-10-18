package com.acme.salarymanagement.employee;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeQueryService {

    private final EmployeeRepository employees;
    private final EmployeeCriteriaValidator criteriaValidator;

    EmployeeQueryService(EmployeeRepository employees, EmployeeCriteriaValidator criteriaValidator) {
        this.employees = employees;
        this.criteriaValidator = criteriaValidator;
    }

    @Transactional(readOnly = true)
    public EmployeeResult getById(long id) {
        return employees.findById(id).map(Employee::toResult).orElseThrow(EmployeeNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public EmployeePageResult list(EmployeePageRequest request) {
        EmployeeReadCriteria criteria = request.criteria();
        criteriaValidator.validate(criteria);
        String pattern = criteria.likePattern();
        long total = criteria.hasCriteria()
                ? employees.countMatching(pattern, criteria.departmentId(), criteria.countryCode(), criteria.currencyCode())
                : employees.count();
        long totalPages = total / request.size() + (total % request.size() == 0 ? 0 : 1);
        long offset = (long) request.page() * request.size();
        // Skip empty pages before asking JPA to represent an unnecessarily large offset.
        List<EmployeeResult> items = List.of();
        if (offset < total) {
            PageRequest page = PageRequest.of(request.page(), request.size(), ordering(request.sort()));
            List<Employee> matches = criteria.hasCriteria()
                    ? employees.findMatching(pattern, criteria.departmentId(), criteria.countryCode(), criteria.currencyCode(), page)
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
}
