package com.acme.salarymanagement.analytics;

import java.math.BigDecimal;
import java.util.List;

public record CompensationAnalyticsResult(CompensationGrouping groupBy, long totalHeadcount,
        List<CurrencySummary> currencySummaries, List<Group> groups) {

    public record CurrencySummary(String currencyCode, long headcount, BigDecimal totalSalary,
            BigDecimal averageSalary, BigDecimal minSalary, BigDecimal maxSalary) {
    }

    public record Group(String groupKey, String groupLabel, String currencyCode, long headcount,
            BigDecimal totalSalary, BigDecimal averageSalary, BigDecimal minSalary, BigDecimal maxSalary) {
    }
}
