package com.acme.salarymanagement.employee.api;

import java.util.List;
import java.util.Set;

import com.acme.salarymanagement.employee.EmployeePageRequest;
import com.acme.salarymanagement.validation.InputValidationException;
import org.springframework.util.MultiValueMap;

final class EmployeePageParameters {

    private static final Set<String> SUPPORTED = Set.of("page", "size");

    private EmployeePageParameters() {
    }

    static EmployeePageRequest parse(MultiValueMap<String, String> parameters) {
        if (parameters.keySet().stream().anyMatch(key -> !SUPPORTED.contains(key))) {
            throw new InputValidationException("query", "UNSUPPORTED_PARAMETER", "Use only supported query parameters.");
        }
        return new EmployeePageRequest(integer(parameters, "page", 0), integer(parameters, "size", 25));
    }

    private static int integer(MultiValueMap<String, String> parameters, String field, int defaultValue) {
        List<String> values = parameters.get(field);
        if (values == null) {
            return defaultValue;
        }
        if (values.size() != 1) {
            throw new InputValidationException(field, "SINGLE_VALUE_REQUIRED", "Provide this parameter only once.");
        }
        String value = values.getFirst();
        if (value == null || !value.matches("[0-9]+")) {
            throw new InputValidationException(field, "INVALID_FORMAT", "Use ASCII digits without signs or whitespace.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException outOfRange) {
            throw new InputValidationException(field, "OUT_OF_RANGE", "This parameter exceeds the supported integer range.");
        }
    }
}
