package com.acme.salarymanagement.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.hr")
record HrAccountProperties(String username, String passwordHash) {

    HrAccountProperties {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Configure HR_USERNAME explicitly.");
        }
        if (passwordHash == null || !passwordHash.matches("\\$2[aby]\\$(1[0-9]|2[0-9]|3[01])\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException("Configure HR_PASSWORD_HASH as a BCrypt hash with cost at least 10.");
        }
    }

    @Override
    public String toString() {
        return "HrAccountProperties[credentials=REDACTED]";
    }
}
