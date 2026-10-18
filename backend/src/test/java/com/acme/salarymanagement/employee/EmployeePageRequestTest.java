package com.acme.salarymanagement.employee;

import com.acme.salarymanagement.validation.InputValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
