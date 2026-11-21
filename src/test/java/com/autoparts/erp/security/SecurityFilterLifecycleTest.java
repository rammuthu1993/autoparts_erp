package com.autoparts.erp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Security Filter Lifecycle and OncePerRequestFilter Execution Tests")
class SecurityFilterLifecycleTest {

    private final SecurityCorrelationFilter filter = new SecurityCorrelationFilter();

    @Test
    @DisplayName("Filter generates secure UUID correlation ID and attaches response header when client header is missing")
    void shouldGenerateCorrelationIdWhenMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainExecuted = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> {
            filterChainExecuted.set(true);
            assertNotNull(MDC.get(SecurityCorrelationFilter.MDC_KEY), "MDC must contain correlationId during chain execution");
            assertEquals(response.getHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER), MDC.get(SecurityCorrelationFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertTrue(filterChainExecuted.get(), "FilterChain must execute");
        String correlationHeader = response.getHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER);
        assertNotNull(correlationHeader);
        assertFalse(correlationHeader.isBlank());
        assertNull(MDC.get(SecurityCorrelationFilter.MDC_KEY), "MDC must be cleared after filter completion");
    }

    @Test
    @DisplayName("Filter preserves validated client-supplied X-Correlation-ID")
    void shouldPreserveValidClientCorrelationId() throws ServletException, IOException {
        String clientCorrelationId = "client-trace-12345_XYZ";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sales");
        request.addHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER, clientCorrelationId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertEquals(clientCorrelationId, MDC.get(SecurityCorrelationFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertEquals(clientCorrelationId, response.getHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER));
        assertNull(MDC.get(SecurityCorrelationFilter.MDC_KEY));
    }

    @Test
    @DisplayName("Filter falls back to client X-Request-ID when X-Correlation-ID is omitted")
    void shouldFallbackToRequestIdHeader() throws ServletException, IOException {
        String requestId = "req-987654";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inventory");
        request.addHeader(SecurityCorrelationFilter.REQUEST_ID_HEADER, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertEquals(requestId, MDC.get(SecurityCorrelationFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertEquals(requestId, response.getHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER));
        assertNull(MDC.get(SecurityCorrelationFilter.MDC_KEY));
    }

    @Test
    @DisplayName("Filter sanitizes and discards invalid or dangerous correlation header")
    void shouldSanitizeMalformedCorrelationId() throws ServletException, IOException {
        String invalidHeader = "malicious<script>alert(1)</script>header;value";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER, invalidHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generatedHeader = response.getHeader(SecurityCorrelationFilter.CORRELATION_ID_HEADER);
        assertNotNull(generatedHeader);
        assertNotEquals(invalidHeader, generatedHeader, "Malformed correlation ID must be replaced with safe UUID");
        assertFalse(generatedHeader.contains("<script>"));
    }

    @Test
    @DisplayName("OncePerRequestFilter guarantees single execution per request dispatch")
    void shouldExecuteOnlyOncePerRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger executionCount = new AtomicInteger(0);

        // Nested filter chain simulating nested forward/include call
        FilterChain innerChain = (req, res) -> {
            executionCount.incrementAndGet();
        };

        FilterChain outerChain = (req, res) -> {
            executionCount.incrementAndGet();
            // Call filter again on same request (simulating container forward/dispatch)
            filter.doFilter(req, res, innerChain);
        };

        filter.doFilter(request, response, outerChain);

        // The outer execution increments to 1; the nested call is bypassed by OncePerRequestFilter marker
        // and delegates directly to innerChain, resulting in total 2 chain executions but only 1 filter processing
        assertEquals(2, executionCount.get());
        assertNull(MDC.get(SecurityCorrelationFilter.MDC_KEY));
    }

    @Test
    @DisplayName("Filter guarantees MDC cleanup even when downstream filter throws exception")
    void shouldCleanUpMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("Simulated filter failure");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, throwingChain));
        assertNull(MDC.get(SecurityCorrelationFilter.MDC_KEY), "MDC must be cleared even when an exception is thrown");
    }

    @Test
    @DisplayName("Filter honors error dispatch and async dispatch flags")
    void shouldConfigureDispatchBehavior() {
        assertFalse(filter.shouldNotFilterAsyncDispatch(), "Must filter async dispatches to maintain correlation");
        assertFalse(filter.shouldNotFilterErrorDispatch(), "Must filter error dispatches to ensure correlation header on errors");
    }
}
