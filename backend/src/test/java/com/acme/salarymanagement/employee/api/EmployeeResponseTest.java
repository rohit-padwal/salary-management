package com.acme.salarymanagement.employee.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.acme.salarymanagement.employee.EmployeeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeResponseTest {

    @Test
    void preservesIdsBeyondJavascriptSafeIntegerRangeAndExactDecimalFormatting() {
        Instant timestamp = Instant.parse("2026-10-18T10:00:00Z");
        EmployeeResult result = new EmployeeResult(Long.MAX_VALUE, "EMP-1", "Asha Rao",
                9007199254740993L, "Engineering", "IN", "India", new BigDecimal("999999999999999.99"),
                "USD", 9007199254740995L, timestamp, timestamp);
        EmployeeResponse response = EmployeeResponse.from(result);
        assertEquals("9223372036854775807", response.id());
        assertEquals("9007199254740993", response.departmentId());
        assertEquals("9007199254740995", response.version());
        assertEquals("999999999999999.99", response.salaryAmount());
        assertEquals("2026-10-18T10:00:00Z", response.createdAt());
    }
}
