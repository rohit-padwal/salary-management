package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeePageRequestTest {

    @ParameterizedTest
    @CsvSource({"-1,25,page", "0,0,size", "0,-1,size", "0,101,size"})
    void rejectsInvalidRequestsFromNonHttpCallers(int page, int size, String field) {
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> new EmployeePageRequest(page, size));
        assertEquals(field, failure.field());
        assertEquals("OUT_OF_RANGE", failure.code());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void treatsMissingAndBlankSearchAsAbsent(String query) {
        assertNull(new EmployeePageRequest(0, 25, query).q());
    }

    @Test
    void stripsOuterWhitespaceAndPreservesInternalTextAndCase() {
        assertEquals("Zoë  李", new EmployeePageRequest(0, 25, " \u2003Zoë  李\t\n").q());
    }

    @Test
    void validatesLengthInUnicodeCodePointsAfterTrimming() {
        String maximum = "𠀀".repeat(200);
        assertEquals(maximum, new EmployeePageRequest(0, 25, " " + maximum + " ").q());
        for (String invalid : new String[]{maximum + "李", "a".repeat(201)}) {
            InputValidationException failure = assertThrows(InputValidationException.class,
                    () -> new EmployeePageRequest(0, 25, invalid));
            assertEquals("q", failure.field());
            assertEquals("TOO_LONG", failure.code());
        }
    }

    @Test
    void rejectsNullCharactersBeforeQueryingPostgres() {
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> new EmployeePageRequest(0, 25, "private\0search"));
        assertEquals("q", failure.field());
        assertEquals("INVALID_FORMAT", failure.code());
    }
}
