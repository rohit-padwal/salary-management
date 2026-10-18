package com.acme.salarymanagement.security.exception;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityProblemWriter {

    private final ObjectMapper json;

    SecurityProblemWriter(ObjectMapper json) {
        this.json = json;
    }

    public void authenticationRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, 401, "Authentication required", "Sign in to access this resource.", "AUTHENTICATION_REQUIRED");
    }

    public void invalidCredentials(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, 401, "Invalid credentials", "Username or password is incorrect.", "INVALID_CREDENTIALS");
    }

    public void unsupportedLoginContentType(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, 415, "Unsupported media type", "Login requires application/x-www-form-urlencoded.", "UNSUPPORTED_MEDIA_TYPE");
    }

    public void accessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure) throws IOException {
        if (failure instanceof CsrfException) {
            write(request, response, 403, "Invalid CSRF token", "Refresh the CSRF token and try again.", "CSRF_INVALID");
        } else {
            write(request, response, 403, "Access denied", "You do not have access to this resource.", "ACCESS_DENIED");
        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
            int status, String title, String detail, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:salary-management:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), problem);
    }
}
