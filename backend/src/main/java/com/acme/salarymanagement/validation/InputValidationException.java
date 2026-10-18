package com.acme.salarymanagement.validation;

public class InputValidationException extends RuntimeException {

    private final String field;
    private final String code;

    public InputValidationException(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public String code() {
        return code;
    }
}
