package com.acme.salarymanagement.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiExceptionHandlerTest {

    @Test
    void unexpectedFailureReturnsGenericProblemWithoutRejectedValuesOrQueryParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/employees");
        request.setQueryString("q=private-search");
        var response = new ApiExceptionHandler().unexpected(
                new DataIntegrityViolationException("SQL detail: salary_amount=9876543.21, secret credential"),
                new ServletWebRequest(request));
        assertEquals(500, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertEquals("INTERNAL_ERROR", body.getProperties().get("code"));
        assertEquals("/api/v1/employees", body.getInstance().toString());
        assertFalse(body.toString().contains("9876543.21"));
        assertFalse(body.toString().contains("secret credential"));
        assertFalse(body.toString().contains("private-search"));
        assertFalse(body.toString().contains("DataIntegrityViolationException"));
    }
}
