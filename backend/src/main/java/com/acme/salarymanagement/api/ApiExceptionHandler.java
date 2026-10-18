package com.acme.salarymanagement.api;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.acme.salarymanagement.employee.EmployeeNumberConflictException;
import com.acme.salarymanagement.employee.EmployeeNotFoundException;
import com.acme.salarymanagement.employee.StaleEmployeeException;
import com.acme.salarymanagement.validation.InputValidationException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InputValidationException.class)
    ResponseEntity<Object> invalidInput(InputValidationException failure, WebRequest request) {
        ProblemDetail problem = problem(400, "VALIDATION_ERROR", "Invalid request", "Correct the indicated fields.", request);
        problem.setProperty("errors", List.of(new FieldError(failure.field(), failure.code(), failure.getMessage())));
        return response(problem, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(EmployeeNumberConflictException.class)
    ResponseEntity<Object> duplicateEmployee(EmployeeNumberConflictException failure, WebRequest request) {
        return response(problem(409, "EMPLOYEE_NUMBER_CONFLICT", "Employee number conflict",
                "Employee number is already in use.", request), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    ResponseEntity<Object> employeeNotFound(EmployeeNotFoundException failure, WebRequest request) {
        return response(problem(404, "EMPLOYEE_NOT_FOUND", "Employee not found",
                "Employee was not found.", request), HttpHeaders.EMPTY);
    }

    @ExceptionHandler({StaleEmployeeException.class, OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<Object> staleEmployee(Exception failure, WebRequest request) {
        return response(problem(409, "STALE_EMPLOYEE", "Stale employee",
                "Employee changed since it was loaded. Reload it before saving.", request), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception failure, WebRequest request) {
        // Record the failure type without logging request bodies or database exception messages.
        LOG.error("Unexpected API failure of type {}", failure.getClass().getName());
        return response(problem(500, "INTERNAL_ERROR", "Internal server error",
                "An unexpected error occurred. Try again later.", request), HttpHeaders.EMPTY);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException failure,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(400, "VALIDATION_ERROR", "Invalid request", "Correct the indicated fields.", request);
        problem.setProperty("errors", failure.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "REQUIRED", "This field is required."))
                .sorted(Comparator.comparing(FieldError::field)).toList());
        return response(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = switch (status.value()) {
            case 400 -> problem(400, "INVALID_REQUEST", "Invalid request", "Provide a valid JSON body with supported fields and types.", request);
            case 404 -> problem(404, "RESOURCE_NOT_FOUND", "Resource not found", "The requested resource was not found.", request);
            case 405 -> problem(405, "METHOD_NOT_ALLOWED", "Method not allowed", "This HTTP method is not supported for this resource.", request);
            case 406 -> problem(406, "NOT_ACCEPTABLE", "Not acceptable", "Request a supported response media type.", request);
            case 415 -> problem(415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", "Use a supported request content type.", request);
            default -> problem(status.value(), status.is5xxServerError() ? "INTERNAL_ERROR" : "INVALID_REQUEST",
                    "Request failed", "The request could not be completed.", request);
        };
        return response(problem, headers);
    }

    private ProblemDetail problem(int status, String code, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:salary-management:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }

    private ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(problem.getStatus()).headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private record FieldError(String field, String code, String message) {
    }
}
