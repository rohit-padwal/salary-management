package com.acme.salarymanagement.api;

import com.acme.salarymanagement.validation.InputValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositiveIdTest {

    @ParameterizedTest
    @CsvSource({"1,1", "000123,123", "9007199254740993,9007199254740993", "9223372036854775807,9223372036854775807"})
    void parsesPositiveAsciiIntegerStringsExactly(String input, long expected) {
        assertEquals(expected, PositiveId.parse(input, "id", "Employee ID"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void requiresAValue(String input) {
        assertInvalid(input, "REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "+1", "1.0", "1e0", " 1", "1 ", "abc", "١", "１２"})
    void rejectsSignsNonAsciiDigitsAndNonIntegerSyntax(String input) {
        assertInvalid(input, "INVALID_FORMAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "000"})
    void requiresAPositiveValue(String input) {
        assertInvalid(input, "MUST_BE_POSITIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "999999999999999999999999999999"})
    void rejectsValuesOutsideLongRangeWithoutEchoingInput(String input) {
        assertInvalid(input, "OUT_OF_RANGE");
    }

    private void assertInvalid(String input, String code) {
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> PositiveId.parse(input, "id", "Employee ID"));
        assertEquals("id", failure.field());
        assertEquals(code, failure.code());
    }
}
