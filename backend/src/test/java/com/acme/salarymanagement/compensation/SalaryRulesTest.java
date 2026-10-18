package com.acme.salarymanagement.compensation;

import java.math.BigDecimal;

import com.acme.salarymanagement.validation.InputValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SalaryRulesTest {

    @ParameterizedTest
    @CsvSource({
            "125000.12,2,125000.12", "1,2,1.00", "1200.0000,0,1200",
            "12.3450,3,12.345", "0.0001,4,0.0001", "0.01,2,0.01",
            "999999999999999.99,2,999999999999999.99",
            "999999999999999.9999,4,999999999999999.9999"
    })
    void acceptsExactAmountsAndInsignificantTrailingZeros(String amount, int minorUnits, String expected) {
        assertEquals(new BigDecimal(expected), SalaryRules.validate(amount, minorUnits));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1e3", "1E3", "NaN", "Infinity", "1,000", "$10", "+10", ".5", "1.", " 10", "10 ", "١٢"})
    void rejectsMissingOrNonPlainDecimalInput(String amount) {
        InputValidationException error = assertThrows(InputValidationException.class,
                () -> SalaryRules.validate(amount, 2));
        assertEquals("salaryAmount", error.field());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void requiresPositiveSalary(String amount) {
        assertEquals("MUST_BE_POSITIVE", assertThrows(InputValidationException.class,
                () -> SalaryRules.validate(amount, 2)).code());
    }

    @ParameterizedTest
    @CsvSource({"1.001,2", "1.1,0", "1.2345,3", "0.00001,4", "999999999999999.99999,4"})
    void rejectsMeaningfulExcessPrecisionWithoutRounding(String amount, int minorUnits) {
        assertEquals("EXCESS_PRECISION", assertThrows(InputValidationException.class,
                () -> SalaryRules.validate(amount, minorUnits)).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000000000", "1000000000000000.0000"})
    void rejectsAmountsOutsideStorageRange(String amount) {
        assertEquals("OUT_OF_RANGE", assertThrows(InputValidationException.class,
                () -> SalaryRules.validate(amount, 2)).code());
    }

    @Test
    void doesNotIncludeRejectedSalaryInValidationMessage() {
        InputValidationException error = assertThrows(InputValidationException.class,
                () -> SalaryRules.validate("1234567.891", 2));
        assertEquals("Salary has more fractional digits than the selected currency allows.", error.getMessage());
    }
}
