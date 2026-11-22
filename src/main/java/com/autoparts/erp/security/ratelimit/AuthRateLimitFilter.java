package com.autoparts.erp.security.ratelimit;

import com.autoparts.erp.common.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Security filter providing IP-based rate limiting on sensitive authentication endpoints
 * (login, token refresh) to mitigate credential brute-forcing, password spraying, and token replay flooding.
 *
 * <p>Keyed strictly on client IP address, ensuring that malicious attempts cannot cause
 * denial-of-service or account lockout for legitimate users.</p>
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    public static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(
            RateLimiter rateLimiter,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper
    ) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String category = resolveRateLimitCategory(request);
        if (category == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIpResolver.resolveClientIp(request);
        RateLimitResult result = rateLimiter.tryAcquire(clientIp, category);

        if (!result.allowed()) {
            log.warn("Authentication rate limit exceeded for client IP [{}] on [{}] {}. Retry after {}s",
                    clientIp, request.getMethod(), request.getRequestURI(), result.retryAfterSeconds());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            response.setHeader(HEADER_RATE_LIMIT_LIMIT, String.valueOf(result.limit()));
            response.setHeader(HEADER_RATE_LIMIT_REMAINING, "0");

            ApiError error = new ApiError(
                    Instant.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests",
                    "Too many authentication attempts from this IP address. Please retry in "
                            + result.retryAfterSeconds() + " seconds.",
                    request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        response.setHeader(HEADER_RATE_LIMIT_LIMIT, String.valueOf(result.limit()));
        response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(result.remainingPermits()));

        filterChain.doFilter(request, response);
    }

    private String resolveRateLimitCategory(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if ("/api/v1/auth/login".equals(path)) {
            return InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN;
        }
        if ("/api/v1/auth/refresh".equals(path)) {
            return InMemorySlidingWindowRateLimiter.CATEGORY_REFRESH;
        }
        return null;
    }
}
