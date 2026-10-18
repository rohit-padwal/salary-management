package com.acme.salarymanagement.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CompensationRollup {

    private CompensationRollup() {
    }

    static CompensationAnalyticsResult summarize(CompensationGrouping grouping, List<CompensationAggregate> rows) {
        List<CompensationAnalyticsResult.Group> groups = new ArrayList<>();
        Map<String, CurrencyTotals> totals = new TreeMap<>();
        long headcount = 0;
        for (CompensationAggregate row : rows) {
            var statistics = summary(row.currencyCode(), row.minorUnits(), row.headcount(),
                    row.totalSalary(), row.minSalary(), row.maxSalary());
            groups.add(new CompensationAnalyticsResult.Group(row.groupKey(), row.groupLabel(), row.currencyCode(),
                    statistics.headcount(), statistics.totalSalary(), statistics.averageSalary(),
                    statistics.minSalary(), statistics.maxSalary()));
            CurrencyTotals current = new CurrencyTotals(row.minorUnits(), row.headcount(),
                    row.totalSalary(), row.minSalary(), row.maxSalary());
            totals.merge(row.currencyCode(), current, CurrencyTotals::plus);
            headcount = Math.addExact(headcount, row.headcount());
        }
        List<CompensationAnalyticsResult.CurrencySummary> summaries = totals.entrySet().stream()
                .map(entry -> entry.getValue().summary(entry.getKey())).toList();
        return new CompensationAnalyticsResult(grouping, headcount, summaries, List.copyOf(groups));
    }

    private static CompensationAnalyticsResult.CurrencySummary summary(String currency, int units, long count,
            BigDecimal total, BigDecimal minimum, BigDecimal maximum) {
        return new CompensationAnalyticsResult.CurrencySummary(currency, count,
                total.setScale(units, RoundingMode.UNNECESSARY),
                total.divide(BigDecimal.valueOf(count), units, RoundingMode.HALF_EVEN),
                minimum.setScale(units, RoundingMode.UNNECESSARY), maximum.setScale(units, RoundingMode.UNNECESSARY));
    }

    private record CurrencyTotals(int minorUnits, long headcount, BigDecimal total, BigDecimal minimum, BigDecimal maximum) {

        CurrencyTotals plus(CurrencyTotals other) {
            return new CurrencyTotals(minorUnits, Math.addExact(headcount, other.headcount),
                    total.add(other.total), minimum.min(other.minimum), maximum.max(other.maximum));
        }

        CompensationAnalyticsResult.CurrencySummary summary(String currency) {
            return CompensationRollup.summary(currency, minorUnits, headcount, total, minimum, maximum);
        }
    }
}
