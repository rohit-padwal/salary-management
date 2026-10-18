package com.acme.salarymanagement.analytics.service;

import java.math.BigDecimal;
import java.util.List;

import com.acme.salarymanagement.analytics.model.CompensationAggregate;
import com.acme.salarymanagement.analytics.model.CompensationAnalyticsResult;
import com.acme.salarymanagement.analytics.model.CompensationGrouping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompensationRollupTest {

    @Test
    void weightsCurrencyAveragesByHeadcountInsteadOfAveragingGroupAverages() {
        CompensationAnalyticsResult result = CompensationRollup.summarize(CompensationGrouping.DEPARTMENT, List.of(
                row("10", "Engineering", "USD", 2, 1, "100", "100", "100"),
                row("20", "Finance", "USD", 2, 3, "30", "10", "10")));
        assertEquals(4, result.totalHeadcount());
        assertEquals(2, result.groups().size());
        var summary = result.currencySummaries().getFirst();
        assertEquals(4, summary.headcount());
        assertEquals(new BigDecimal("130.00"), summary.totalSalary());
        assertEquals(new BigDecimal("32.50"), summary.averageSalary());
        assertEquals(new BigDecimal("10.00"), summary.minSalary());
        assertEquals(new BigDecimal("100.00"), summary.maxSalary());
    }

    @Test
    void currencyAverageUsesExactTotalsInsteadOfRoundedGroupAverages() {
        CompensationAnalyticsResult result = CompensationRollup.summarize(CompensationGrouping.DEPARTMENT, List.of(
                row("10", "Engineering", "USD", 2, 2, "0.03", "0.01", "0.02"),
                row("20", "Finance", "USD", 2, 1, "0.01", "0.01", "0.01")));
        assertEquals(new BigDecimal("0.02"), result.groups().getFirst().averageSalary());
        assertEquals(new BigDecimal("0.01"), result.currencySummaries().getFirst().averageSalary());
        assertEquals(new BigDecimal("0.04"), result.currencySummaries().getFirst().totalSalary());
    }

    @ParameterizedTest
    @CsvSource({"USD,2,2000.01,1000.00", "USD,2,2000.03,1000.02", "JPY,0,2001,1000", "JPY,0,2003,1002",
            "KWD,3,2.469,1.234", "KWD,3,2.471,1.236", "TST,4,2.0001,1.0000", "TST,4,2.0003,1.0002"})
    void roundsAveragesOnceWithHalfEvenAtCurrencyPrecision(String currency, int units, String total, String average) {
        BigDecimal maximum = new BigDecimal(total).subtract(BigDecimal.ONE);
        CompensationAnalyticsResult result = CompensationRollup.summarize(CompensationGrouping.COUNTRY,
                List.of(row("IN", "India", currency, units, 2, total, "1", maximum.toPlainString())));
        assertEquals(new BigDecimal(average), result.groups().getFirst().averageSalary());
        assertEquals(new BigDecimal(average), result.currencySummaries().getFirst().averageSalary());
    }

    @Test
    void separatesCurrenciesAndOrdersCurrencySummariesByCode() {
        CompensationAnalyticsResult result = CompensationRollup.summarize(CompensationGrouping.COUNTRY, List.of(
                row("IN", "India", "USD", 2, 1, "100", "100", "100"),
                row("IN", "India", "JPY", 0, 2, "2001", "1000", "1001"),
                row("IN", "India", "KWD", 3, 2, "2.469", "1.234", "1.235")));
        assertEquals(5, result.totalHeadcount());
        assertEquals(List.of("JPY", "KWD", "USD"), result.currencySummaries().stream()
                .map(CompensationAnalyticsResult.CurrencySummary::currencyCode).toList());
        assertEquals(new BigDecimal("2001"), result.currencySummaries().get(0).totalSalary());
        assertEquals(new BigDecimal("2.469"), result.currencySummaries().get(1).totalSalary());
        assertEquals(new BigDecimal("100.00"), result.currencySummaries().get(2).totalSalary());
    }

    @Test
    void preservesTotalsBeyondTheEmployeeColumnRange() {
        CompensationAnalyticsResult result = CompensationRollup.summarize(CompensationGrouping.DEPARTMENT,
                List.of(row("10", "Engineering", "USD", 2, 2, "1999999999999999.98", "999999999999999.99", "999999999999999.99")));
        assertEquals(new BigDecimal("1999999999999999.98"), result.groups().getFirst().totalSalary());
        assertEquals(new BigDecimal("1999999999999999.98"), result.currencySummaries().getFirst().totalSalary());
        assertEquals(new BigDecimal("999999999999999.99"), result.currencySummaries().getFirst().averageSalary());
    }

    @Test
    void emptyDataReturnsZeroHeadcountAndNoInventedGroups() {
        CompensationAnalyticsResult result = CompensationRollup.summarize(CompensationGrouping.COUNTRY, List.of());
        assertEquals(CompensationGrouping.COUNTRY, result.groupBy());
        assertEquals(0, result.totalHeadcount());
        assertTrue(result.groups().isEmpty());
        assertTrue(result.currencySummaries().isEmpty());
    }

    private CompensationAggregate row(String key, String label, String currency, int units, long count,
            String total, String minimum, String maximum) {
        return new CompensationAggregate(key, label, currency, units, count,
                new BigDecimal(total).setScale(4), new BigDecimal(minimum).setScale(4), new BigDecimal(maximum).setScale(4));
    }
}
