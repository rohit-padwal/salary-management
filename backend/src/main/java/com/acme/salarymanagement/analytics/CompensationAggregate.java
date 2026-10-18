package com.acme.salarymanagement.analytics;

import java.math.BigDecimal;

record CompensationAggregate(String groupKey, String groupLabel, String currencyCode, int minorUnits,
        long headcount, BigDecimal totalSalary, BigDecimal minSalary, BigDecimal maxSalary) {
}
