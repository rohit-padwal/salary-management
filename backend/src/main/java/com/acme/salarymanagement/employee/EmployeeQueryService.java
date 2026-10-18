package com.acme.salarymanagement.employee;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeQueryService {

    private final EmployeeRepository employees;

    EmployeeQueryService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public EmployeeResult getById(long id) {
        return employees.findById(id).map(Employee::toResult).orElseThrow(EmployeeNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public EmployeePageResult list(EmployeePageRequest request) {
        String pattern = literalPattern(request.q());
        long total = pattern == null ? employees.count() : employees.countMatching(pattern);
        long totalPages = total / request.size() + (total % request.size() == 0 ? 0 : 1);
        long offset = (long) request.page() * request.size();
        // Skip empty pages before asking JPA to represent an unnecessarily large offset.
        List<EmployeeResult> items = List.of();
        if (offset < total) {
            PageRequest page = PageRequest.of(request.page(), request.size(), Sort.by("employeeNumber", "id"));
            List<Employee> matches = pattern == null ? employees.findAllBy(page) : employees.findMatching(pattern, page);
            items = matches.stream().map(Employee::toResult).toList();
        }
        return new EmployeePageResult(items, request.page(), request.size(), total, totalPages);
    }

    private static String literalPattern(String query) {
        if (query == null) {
            return null;
        }
        // Escape the escape character first so later wildcard escapes remain intact.
        return "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
