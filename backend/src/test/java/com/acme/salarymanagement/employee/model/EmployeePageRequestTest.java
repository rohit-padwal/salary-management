package com.acme.salarymanagement.employee.model;

import com.acme.salarymanagement.validation.InputValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"\u00a0", "\u2007", "\u202f", "\ufeff"})
    void preservesSignificantOuterCharactersInLiteralSearch(String character) {
        String literal = character + "Zoë" + character;
        assertEquals(literal, new EmployeePageRequest(0, 25, " \u001c" + literal + "\u001f ").q());
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> new EmployeePageRequest(0, 25, character + "李".repeat(199) + character));
        assertEquals("TOO_LONG", failure.code());
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

    @Test
    void normalizesOptionalCodesWithoutInferringCurrencyFromCountry() {
        EmployeePageRequest request = new EmployeePageRequest(0, 25, null, 1L, " in ", " usd ");
        assertEquals(1L, request.departmentId());
        assertEquals("IN", request.countryCode());
        assertEquals("USD", request.currencyCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonpositiveDepartmentFiltersFromNonHttpCallers(long id) {
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> new EmployeePageRequest(0, 25, null, id, null, null));
        assertEquals("departmentId", failure.field());
        assertEquals("MUST_BE_POSITIVE", failure.code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"countryCode", "currencyCode"})
    void suppliedBlankCodeFiltersAreInvalid(String field) {
        for (String blank : new String[]{"", " ", "\t\n"}) {
            InputValidationException failure = assertThrows(InputValidationException.class,
                    () -> new EmployeePageRequest(0, 25, null, null,
                            field.equals("countryCode") ? blank : null, field.equals("currencyCode") ? blank : null));
            assertEquals(field, failure.field());
            assertEquals("REQUIRED", failure.code());
        }
    }

    @ParameterizedTest
    @CsvSource({"countryCode,I", "countryCode,USA", "countryCode,12", "countryCode,ß",
            "currencyCode,US", "currencyCode,USDD", "currencyCode,U$D", "currencyCode,ßßß"})
    void validatesAsciiCodeShapeBeforeUppercasing(String field, String value) {
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> new EmployeePageRequest(0, 25, null, null,
                        field.equals("countryCode") ? value : null, field.equals("currencyCode") ? value : null));
        assertEquals(field, failure.field());
        assertEquals("INVALID_FORMAT", failure.code());
    }

    @Test
    void defaultsToEmployeeNumberAscendingForAbsentSort() {
        assertEquals(EmployeeSort.DEFAULT, new EmployeePageRequest(0, 25).sort());
        assertEquals(EmployeeSort.DEFAULT, new EmployeePageRequest(0, 25, null, null, null, null, null).sort());
    }

    @ParameterizedTest
    @EnumSource(EmployeeSort.Direction.class)
    void salarySortRequiresAnExplicitCurrencyForNonHttpCallers(EmployeeSort.Direction direction) {
        EmployeeSort sort = new EmployeeSort(EmployeeSort.Field.SALARY_AMOUNT, direction);
        InputValidationException failure = assertThrows(InputValidationException.class,
                () -> new EmployeePageRequest(0, 25, null, null, "IN", null, sort));
        assertEquals("sort", failure.field());
        assertEquals("CURRENCY_REQUIRED", failure.code());
        EmployeePageRequest accepted = new EmployeePageRequest(0, 25, null, null, "IN", " usd ", sort);
        assertEquals("USD", accepted.currencyCode());
        assertEquals(sort, accepted.sort());
    }

    @Test
    void typedSortRequiresBothFieldAndDirection() {
        assertThrows(InputValidationException.class, () -> new EmployeeSort(null, EmployeeSort.Direction.ASC));
        assertThrows(InputValidationException.class, () -> new EmployeeSort(EmployeeSort.Field.FULL_NAME, null));
    }
}
