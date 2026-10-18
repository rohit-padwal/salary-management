package com.acme.salarymanagement.security.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HrAccountPropertiesTest {

    private static final String HASH = new BCryptPasswordEncoder(10).encode("synthetic-test-password");

    @Test
    void acceptsConfiguredAccountWithBcryptHash() {
        HrAccountProperties properties = new HrAccountProperties("hr-manager", HASH);
        assertEquals("hr-manager", properties.username());
        assertEquals(HASH, properties.passwordHash());
        assertFalse(properties.toString().contains(HASH));
        assertFalse(properties.toString().contains("hr-manager"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    void requiresAnExplicitUsername(String username) {
        assertThrows(IllegalArgumentException.class, () -> new HrAccountProperties(username, HASH));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"plaintext-password", "{noop}password", "$2a$12$invalid", "  "})
    void rejectsMissingPlaintextAndMalformedHashesWithoutEchoingThem(String hash) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HrAccountProperties("hr-manager", hash));
        assertEquals("Configure HR_PASSWORD_HASH as a BCrypt hash with cost at least 10.", error.getMessage());
    }

    @Test
    void rejectsLowCostBcryptHash() {
        String weakHash = new BCryptPasswordEncoder(4).encode("synthetic-test-password");
        assertThrows(IllegalArgumentException.class, () -> new HrAccountProperties("hr-manager", weakHash));
    }
}
