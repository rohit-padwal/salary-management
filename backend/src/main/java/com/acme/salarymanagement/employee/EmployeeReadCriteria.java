package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;
import com.acme.salarymanagement.validation.ReferenceCode;

public record EmployeeReadCriteria(String q, Long departmentId, String countryCode, String currencyCode) {

    public static final EmployeeReadCriteria EMPTY = new EmployeeReadCriteria(null, null, null, null);

    public EmployeeReadCriteria {
        q = normalizeSearch(q);
        if (departmentId != null && departmentId <= 0) {
            throw new InputValidationException("departmentId", "MUST_BE_POSITIVE", "Select a supported department.");
        }
        if (countryCode != null) {
            countryCode = ReferenceCode.normalize(countryCode, "countryCode", 2);
        }
        if (currencyCode != null) {
            currencyCode = ReferenceCode.normalize(currencyCode, "currencyCode", 3);
        }
    }

    public boolean hasCriteria() {
        return q != null || departmentId != null || countryCode != null || currencyCode != null;
    }

    public String likePattern() {
        if (q == null) {
            return null;
        }
        // Escape the escape character first so later wildcard escapes remain intact.
        return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > 200) {
            throw new InputValidationException("q", "TOO_LONG", "Search must contain at most 200 characters.");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new InputValidationException("q", "INVALID_FORMAT", "Search cannot contain a null character.");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
