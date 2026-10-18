package com.acme.salarymanagement.analytics.api;

import java.util.List;
import java.util.Set;

import com.acme.salarymanagement.analytics.CompensationGrouping;
import com.acme.salarymanagement.analytics.CompensationQuery;
import com.acme.salarymanagement.api.PositiveId;
import com.acme.salarymanagement.employee.EmployeeReadCriteria;
import com.acme.salarymanagement.validation.InputValidationException;
import org.springframework.util.MultiValueMap;

final class CompensationParameters {

    private static final Set<String> SUPPORTED = Set.of("groupBy", "q", "departmentId", "countryCode", "currencyCode");

    private CompensationParameters() {
    }

    static CompensationQuery parse(MultiValueMap<String, String> parameters) {
        if (parameters.keySet().stream().anyMatch(key -> !SUPPORTED.contains(key))) {
            throw new InputValidationException("query", "UNSUPPORTED_PARAMETER", "Use only supported query parameters.");
        }
        CompensationGrouping grouping = grouping(parameters);
        Long departmentId = parameters.containsKey("departmentId")
                ? PositiveId.parse(singleValue(parameters, "departmentId"), "departmentId", "Department ID") : null;
        return new CompensationQuery(grouping, new EmployeeReadCriteria(singleValue(parameters, "q"), departmentId,
                singleValue(parameters, "countryCode"), singleValue(parameters, "currencyCode")));
    }

    private static CompensationGrouping grouping(MultiValueMap<String, String> parameters) {
        if (!parameters.containsKey("groupBy")) {
            return CompensationGrouping.DEPARTMENT;
        }
        String value = singleValue(parameters, "groupBy");
        return switch (value == null ? "" : value) {
            case "department" -> CompensationGrouping.DEPARTMENT;
            case "country" -> CompensationGrouping.COUNTRY;
            default -> throw new InputValidationException("groupBy", "INVALID_FORMAT", "Choose department or country.");
        };
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
