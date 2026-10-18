package com.acme.salarymanagement.api.exception;

import java.util.stream.Stream;

import com.acme.salarymanagement.employee.exception.StaleEmployeeException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ApiExceptionHandlerTest {

    @ParameterizedTest
    @MethodSource("optimisticFailures")
    void mvcMapsBothStalePreconditionsAndPersistenceFailuresToSafeConflict(Exception failure) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController(failure))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var response = mvc.perform(get("/test-conflict")).andReturn().getResponse();
        assertEquals(409, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals("STALE_EMPLOYEE", body.path("code").asString());
        assertFalse(response.getContentAsString().contains("internal version data"));
        assertFalse(body.has("exception"));
    }

    static Stream<Exception> optimisticFailures() {
        return Stream.of(new StaleEmployeeException(),
                new ObjectOptimisticLockingFailureException("Employee", 1L),
                new OptimisticLockException("internal version data"));
    }

    @RestController
    static class FailingController {
        private final Exception failure;

        FailingController(Exception failure) {
            this.failure = failure;
        }

        @GetMapping("/test-conflict")
        void fail() throws Exception {
            throw failure;
        }
    }

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
