package com.acme.salarymanagement.compensation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

import com.acme.salarymanagement.validation.InputValidationException;

public final class SalaryRules {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
    private static final BigDecimal STORAGE_LIMIT = new BigDecimal("1000000000000000");

    private SalaryRules() {
    }

    public static BigDecimal validate(String amount, int minorUnits) {
        if (minorUnits < 0 || minorUnits > 4) {
            throw new IllegalArgumentException("Unsupported currency precision metadata.");
        }
        if (amount == null || amount.isBlank()) {
            throw invalid("REQUIRED", "Salary is required.");
        }
        if (!PLAIN_DECIMAL.matcher(amount).matches()) {
            throw invalid("INVALID_FORMAT", "Salary must be a plain decimal without symbols or separators.");
        }

        BigDecimal value = new BigDecimal(amount);
        if (value.signum() <= 0) {
            throw invalid("MUST_BE_POSITIVE", "Salary must be greater than zero.");
        }
        if (value.stripTrailingZeros().scale() > minorUnits) {
            throw invalid("EXCESS_PRECISION", "Salary has more fractional digits than the selected currency allows.");
        }
        if (value.compareTo(STORAGE_LIMIT) >= 0) {
            throw invalid("OUT_OF_RANGE", "Salary exceeds the supported storage range.");
        }
        return value.setScale(minorUnits, RoundingMode.UNNECESSARY);
    }

    private static InputValidationException invalid(String code, String message) {
        return new InputValidationException("salaryAmount", code, message);
    }
}
