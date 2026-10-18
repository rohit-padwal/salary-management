package com.acme.salarymanagement.analytics.model;

import java.math.BigDecimal;

public record CompensationAggregate(String groupKey, String groupLabel, String currencyCode, int minorUnits,
        long headcount, BigDecimal totalSalary, BigDecimal minSalary, BigDecimal maxSalary) {
}
