package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;

public record EmployeeSort(Field field, Direction direction) {

    public static final EmployeeSort DEFAULT = new EmployeeSort(Field.EMPLOYEE_NUMBER, Direction.ASC);

    public EmployeeSort {
        if (field == null || direction == null) {
            throw new InputValidationException("sort", "INVALID_FORMAT", "Choose a supported sort field and asc or desc.");
        }
    }

    public enum Field {
        EMPLOYEE_NUMBER, FULL_NAME, DEPARTMENT_NAME, COUNTRY_CODE, CURRENCY_CODE, SALARY_AMOUNT
    }

    public enum Direction {
        ASC, DESC
    }
}
