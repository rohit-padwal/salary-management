package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeeInputTest {

    @Test
    void normalizesIdentifiersAndPreservesInternationalNameAndInternalSpaces() {
        EmployeeInput input = EmployeeInput.validate(command(" emp_01-x ", "  Zoë  李  ", 1L, " in ", " usd "));
        assertEquals("EMP_01-X", input.employeeNumber());
        assertEquals("Zoë  李", input.fullName());
        assertEquals("IN", input.countryCode());
        assertEquals("USD", input.currencyCode());
        assertEquals("1000.00", input.salaryAmount());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "-A", "_A", "A B", "A!", "é1", "ß1", "Ａ1", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567"})
    void rejectsInvalidEmployeeNumbersBeforeCaseConversion(String number) {
        assertInvalidField("employeeNumber", command(number, "Asha Rao", 1L, "IN", "USD"));
    }

    @Test
    void acceptsMaximumLengthEmployeeNumber() {
        assertEquals("A".repeat(32), EmployeeInput.validate(command("a".repeat(32), "Name", 1L, "IN", "USD")).employeeNumber());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n", "A\u0000B"})
    void rejectsBlankOrUnstorableNames(String name) {
        assertInvalidField("fullName", command("EMP-1", name, 1L, "IN", "USD"));
    }

    @Test
    void countsUnicodeCharactersRatherThanUtf16CodeUnits() {
        String name = "𠀀".repeat(200);
        assertEquals(name, EmployeeInput.validate(command("EMP-1", name, 1L, "IN", "USD")).fullName());
        assertInvalidField("fullName", command("EMP-1", name + "李", 1L, "IN", "USD"));
    }

    @Test
    void rejectsMissingOrNonPositiveDepartmentId() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertInvalidField("departmentId", command("EMP-1", "Name", id, "IN", "USD"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"I", "IND", "1N", "ıN"})
    void rejectsMalformedCountryCodes(String code) {
        assertInvalidField("countryCode", command("EMP-1", "Name", 1L, code, "USD"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"US", "USDD", "U$D", "uſd"})
    void rejectsMalformedCurrencyCodes(String code) {
        assertInvalidField("currencyCode", command("EMP-1", "Name", 1L, "IN", code));
    }

    private CreateEmployeeCommand command(String number, String name, Long departmentId, String country, String currency) {
        return new CreateEmployeeCommand(number, name, departmentId, country, "1000.00", currency);
    }

    private void assertInvalidField(String field, CreateEmployeeCommand command) {
        assertEquals(field, assertThrows(InputValidationException.class, () -> EmployeeInput.validate(command)).field());
    }
}
