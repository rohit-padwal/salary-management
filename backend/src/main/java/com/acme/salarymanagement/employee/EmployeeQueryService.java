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
        long total = employees.count();
        long totalPages = total / request.size() + (total % request.size() == 0 ? 0 : 1);
        long offset = (long) request.page() * request.size();
        // Skip empty pages before asking JPA to represent an unnecessarily large offset.
        List<EmployeeResult> items = offset >= total ? List.of()
                : employees.findAllBy(PageRequest.of(request.page(), request.size(), Sort.by("employeeNumber", "id")))
                        .stream().map(Employee::toResult).toList();
        return new EmployeePageResult(items, request.page(), request.size(), total, totalPages);
    }
}
