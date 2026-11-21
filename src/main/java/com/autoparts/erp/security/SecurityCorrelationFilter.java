package com.autoparts.erp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Security Correlation and Diagnostic Tracing Filter.
 *
 * Implements OncePerRequestFilter to guarantee execution exactly once per request.
 * - Extracts client-provided X-Correlation-ID / X-Request-ID (validated) or generates a secure UUID.
 * - Adds X-Correlation-ID to the HTTP response header.
 * - Binds correlationId to SLF4J MDC for unified audit logging across security and business logs.
 * - Cleans up MDC in a finally block to prevent thread context leakage.
 * - Configured with shouldNotFilterAsyncDispatch = false and shouldNotFilterErrorDispatch = false
 *   to ensure context continuity during async dispatches and error handling.
 */
public class SecurityCorrelationFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);

        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String clientCorrelationId = request.getHeader(CORRELATION_ID_HEADER);
        if (clientCorrelationId == null || clientCorrelationId.isBlank()) {
            clientCorrelationId = request.getHeader(REQUEST_ID_HEADER);
        }
        if (clientCorrelationId != null && SAFE_CORRELATION_ID.matcher(clientCorrelationId.trim()).matches()) {
            return clientCorrelationId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
