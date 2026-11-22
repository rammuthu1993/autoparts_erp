package com.autoparts.erp.security.ratelimit;

import com.autoparts.erp.common.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Authentication Rate Limit Filter Tests")
class AuthRateLimitFilterTest {

    private InMemorySlidingWindowRateLimiter rateLimiter;
    private ClientIpResolver ipResolver;
    private ObjectMapper objectMapper;
    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = new InMemorySlidingWindowRateLimiter(
                2, 60, 3, 60, 10, 60, 1000, System::nanoTime
        );
        ipResolver = new ClientIpResolver(true);
        objectMapper = new ObjectMapper();
        filter = new AuthRateLimitFilter(rateLimiter, ipResolver, objectMapper);
    }

    @Test
    @DisplayName("Filter completely bypasses non-auth endpoints")
    void shouldBypassNonAuthEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertNull(response.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_LIMIT));
    }

    @Test
    @DisplayName("Filter completely bypasses non-POST requests to auth path")
    void shouldBypassNonPostRequestsOnAuthPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertNull(response.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_LIMIT));
    }

    @Test
    @DisplayName("Filter permits POST /api/v1/auth/login under limit and attaches rate limit headers")
    void shouldPermitLoginUnderLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.10.10.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertTrue(chainCalled.get());
        assertEquals(200, response.getStatus()); // default MockHttpServletResponse status is 200 unless modified downstream
        assertEquals("2", response.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_LIMIT));
        assertEquals("1", response.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_REMAINING));
    }

    @Test
    @DisplayName("Filter returns HTTP 429 Too Many Requests when rate limit is exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws ServletException, IOException {
        String clientIp = "192.168.1.100";

        // 1st request -> allowed
        MockHttpServletRequest r1 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        r1.setRemoteAddr(clientIp);
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(r1, res1, (req, res) -> {});
        assertEquals("1", res1.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_REMAINING));

        // 2nd request -> allowed
        MockHttpServletRequest r2 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        r2.setRemoteAddr(clientIp);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(r2, res2, (req, res) -> {});
        assertEquals("0", res2.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_REMAINING));

        // 3rd request -> rejected with 429
        MockHttpServletRequest r3 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        r3.setRemoteAddr(clientIp);
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        AtomicBoolean chainCalled3 = new AtomicBoolean(false);
        filter.doFilter(r3, res3, (req, res) -> chainCalled3.set(true));

        assertFalse(chainCalled3.get(), "Chain must NOT be called when rate limited");
        assertEquals(429, res3.getStatus());
        assertEquals("0", res3.getHeader(AuthRateLimitFilter.HEADER_RATE_LIMIT_REMAINING));
        assertNotNull(res3.getHeader(AuthRateLimitFilter.HEADER_RETRY_AFTER));
        assertTrue(res3.getContentAsString().contains("Too Many Requests"));
    }

    @Test
    @DisplayName("Prevents denial of service against legitimate users by isolating rate limits by client IP")
    void shouldPreventAccountLockoutAbuseAcrossIps() throws ServletException, IOException {
        String attackerIp = "198.51.100.99";
        String legitimateUserIp = "203.0.113.5";

        // Attacker exhausts limit from their IP
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr(attackerIp);
            filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {});
        }

        // Attacker's 3rd request is blocked
        MockHttpServletRequest attackerReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        attackerReq.setRemoteAddr(attackerIp);
        MockHttpServletResponse attackerRes = new MockHttpServletResponse();
        filter.doFilter(attackerReq, attackerRes, (r, s) -> {});
        assertEquals(429, attackerRes.getStatus());

        // Legitimate user on another IP is unaffected and allowed
        MockHttpServletRequest legitReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        legitReq.setRemoteAddr(legitimateUserIp);
        MockHttpServletResponse legitRes = new MockHttpServletResponse();
        AtomicBoolean legitAllowed = new AtomicBoolean(false);
        filter.doFilter(legitReq, legitRes, (r, s) -> legitAllowed.set(true));

        assertTrue(legitAllowed.get(), "Legitimate user from separate IP must not be locked out");
        assertNotEquals(429, legitRes.getStatus());
    }
}
