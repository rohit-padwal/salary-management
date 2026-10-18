package com.acme.salarymanagement.employee.model;
import com.acme.salarymanagement.validation.InputValidationException;

public record EmployeePageRequest(int page, int size, String q,
        Long departmentId, String countryCode, String currencyCode, EmployeeSort sort) {

    public EmployeePageRequest(int page, int size) {
        this(page, size, null);
    }

    public EmployeePageRequest(int page, int size, String q) {
        this(page, size, q, null, null, null);
    }

    public EmployeePageRequest(int page, int size, String q, Long departmentId, String countryCode, String currencyCode) {
        this(page, size, q, departmentId, countryCode, currencyCode, EmployeeSort.DEFAULT);
    }

    public EmployeePageRequest {
        if (page < 0) {
            throw new InputValidationException("page", "OUT_OF_RANGE", "Page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw new InputValidationException("size", "OUT_OF_RANGE", "Size must be between 1 and 100.");
        }
        EmployeeReadCriteria criteria = new EmployeeReadCriteria(q, departmentId, countryCode, currencyCode);
        q = criteria.q();
        departmentId = criteria.departmentId();
        countryCode = criteria.countryCode();
        currencyCode = criteria.currencyCode();
        sort = sort == null ? EmployeeSort.DEFAULT : sort;
        if (sort.field() == EmployeeSort.Field.SALARY_AMOUNT && currencyCode == null) {
            throw new InputValidationException("sort", "CURRENCY_REQUIRED", "Select a currency before sorting by salary.");
        }
    }

    public EmployeeReadCriteria criteria() {
        return new EmployeeReadCriteria(q, departmentId, countryCode, currencyCode);
    }
}
