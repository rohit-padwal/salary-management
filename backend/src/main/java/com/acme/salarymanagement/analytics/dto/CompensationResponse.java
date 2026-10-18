package com.acme.salarymanagement.analytics.dto;

import java.util.List;

import com.acme.salarymanagement.analytics.model.CompensationAnalyticsResult;

public record CompensationResponse(String groupBy, long totalHeadcount,
        List<CurrencySummary> currencySummaries, List<Group> groups) {

    public static CompensationResponse from(CompensationAnalyticsResult result) {
        String grouping = switch (result.groupBy()) {
            case DEPARTMENT -> "department";
            case COUNTRY -> "country";
        };
        return new CompensationResponse(grouping, result.totalHeadcount(),
                result.currencySummaries().stream().map(CurrencySummary::from).toList(),
                result.groups().stream().map(Group::from).toList());
    }

    public record CurrencySummary(String currencyCode, long headcount, String totalSalary,
            String averageSalary, String minSalary, String maxSalary) {

        static CurrencySummary from(CompensationAnalyticsResult.CurrencySummary summary) {
            return new CurrencySummary(summary.currencyCode(), summary.headcount(), summary.totalSalary().toPlainString(),
                    summary.averageSalary().toPlainString(), summary.minSalary().toPlainString(), summary.maxSalary().toPlainString());
        }
    }

    public record Group(String groupKey, String groupLabel, String currencyCode, long headcount,
            String totalSalary, String averageSalary, String minSalary, String maxSalary) {

        static Group from(CompensationAnalyticsResult.Group group) {
            return new Group(group.groupKey(), group.groupLabel(), group.currencyCode(), group.headcount(),
                    group.totalSalary().toPlainString(), group.averageSalary().toPlainString(),
                    group.minSalary().toPlainString(), group.maxSalary().toPlainString());
        }
    }
}
