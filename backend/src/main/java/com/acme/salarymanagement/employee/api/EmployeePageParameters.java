package com.acme.salarymanagement.employee.api;

import java.util.List;
import java.util.Set;

import com.acme.salarymanagement.api.PositiveId;
import com.acme.salarymanagement.employee.EmployeePageRequest;
import com.acme.salarymanagement.employee.EmployeeSort;
import com.acme.salarymanagement.validation.InputValidationException;
import org.springframework.util.MultiValueMap;

final class EmployeePageParameters {

    private static final Set<String> SUPPORTED = Set.of("page", "size", "q", "departmentId", "countryCode", "currencyCode", "sort");

    private EmployeePageParameters() {
    }

    static EmployeePageRequest parse(MultiValueMap<String, String> parameters) {
        if (parameters.keySet().stream().anyMatch(key -> !SUPPORTED.contains(key))) {
            throw new InputValidationException("query", "UNSUPPORTED_PARAMETER", "Use only supported query parameters.");
        }
        Long departmentId = parameters.containsKey("departmentId")
                ? PositiveId.parse(singleValue(parameters, "departmentId"), "departmentId", "Department ID") : null;
        return new EmployeePageRequest(integer(parameters, "page", 0), integer(parameters, "size", 25),
                singleValue(parameters, "q"), departmentId,
                singleValue(parameters, "countryCode"), singleValue(parameters, "currencyCode"), sort(parameters));
    }

    private static EmployeeSort sort(MultiValueMap<String, String> parameters) {
        if (!parameters.containsKey("sort")) {
            return EmployeeSort.DEFAULT;
        }
        String value = singleValue(parameters, "sort");
        String[] parts = value == null ? new String[0] : value.split(",", -1);
        if (parts.length != 2) {
            throw invalidSort();
        }
        EmployeeSort.Field field = switch (parts[0]) {
            case "employeeNumber" -> EmployeeSort.Field.EMPLOYEE_NUMBER;
            case "fullName" -> EmployeeSort.Field.FULL_NAME;
            case "departmentName" -> EmployeeSort.Field.DEPARTMENT_NAME;
            case "countryCode" -> EmployeeSort.Field.COUNTRY_CODE;
            case "currencyCode" -> EmployeeSort.Field.CURRENCY_CODE;
            case "salaryAmount" -> EmployeeSort.Field.SALARY_AMOUNT;
            default -> throw invalidSort();
        };
        EmployeeSort.Direction direction = switch (parts[1]) {
            case "asc" -> EmployeeSort.Direction.ASC;
            case "desc" -> EmployeeSort.Direction.DESC;
            default -> throw invalidSort();
        };
        return new EmployeeSort(field, direction);
    }

    private static InputValidationException invalidSort() {
        return new InputValidationException("sort", "INVALID_FORMAT", "Choose a supported sort field and asc or desc.");
    }

    private static int integer(MultiValueMap<String, String> parameters, String field, int defaultValue) {
        if (!parameters.containsKey(field)) {
            return defaultValue;
        }
        String value = singleValue(parameters, field);
        if (value == null || !value.matches("[0-9]+")) {
            throw new InputValidationException(field, "INVALID_FORMAT", "Use ASCII digits without signs or whitespace.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException outOfRange) {
            throw new InputValidationException(field, "OUT_OF_RANGE", "This parameter exceeds the supported integer range.");
        }
    }

    private static String singleValue(MultiValueMap<String, String> parameters, String field) {
        List<String> values = parameters.get(field);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw new InputValidationException(field, "SINGLE_VALUE_REQUIRED", "Provide this parameter only once.");
        }
        return values.getFirst();
    }
}
