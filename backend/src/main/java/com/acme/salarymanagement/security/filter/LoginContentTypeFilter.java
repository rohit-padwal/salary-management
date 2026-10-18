package com.acme.salarymanagement.security.filter;

import java.io.IOException;

import com.acme.salarymanagement.security.exception.SecurityProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public final class LoginContentTypeFilter extends OncePerRequestFilter {

    private final SecurityProblemWriter problems;

    public LoginContentTypeFilter(SecurityProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/v1/auth/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hasFormContentType(request)) {
            problems.unsupportedLoginContentType(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasFormContentType(HttpServletRequest request) {
        try {
            MediaType type = MediaType.parseMediaType(request.getContentType());
            return "application".equalsIgnoreCase(type.getType())
                    && "x-www-form-urlencoded".equalsIgnoreCase(type.getSubtype());
        } catch (IllegalArgumentException invalidType) {
            return false;
        }
    }
}
