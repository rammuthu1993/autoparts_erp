package com.autoparts.erp.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Client IP Resolver Unit Tests")
class ClientIpResolverTest {

    private static final String DEFAULT_PROXIES = "127.0.0.1,::1,10.0.0.1";

    @Test
    @DisplayName("Direct connection returns remoteAddr and ignores forwarded headers when trustForwardedHeaders is false")
    void shouldIgnoreForwardedHeadersWhenTrustDisabled() {
        ClientIpResolver resolver = new ClientIpResolver(false, DEFAULT_PROXIES);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.195");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("10.0.0.1", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Direct connection from untrusted origin ignores spoofed X-Forwarded-For even if trustForwardedHeaders is true")
    void shouldIgnoreSpoofedHeaderFromUntrustedDirectCaller() {
        ClientIpResolver resolver = new ClientIpResolver(true, "127.0.0.1,::1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "198.51.100.42");
        request.setRemoteAddr("203.0.113.50"); // Untrusted direct caller

        assertEquals("203.0.113.50", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Resolves client IP from single X-Forwarded-For when caller is trusted proxy")
    void shouldResolveFromSingleForwardedForWhenTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "203.0.113.195");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("203.0.113.195", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Traverses multi-hop chain right-to-left and returns first untrusted client IP")
    void shouldResolveFirstUntrustedIpFromRightToLeft() {
        // Chain: Client (198.51.100.42) -> Intermediate Proxy (10.0.0.2) -> Edge Proxy (10.0.0.1) -> App
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1,10.0.0.2");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "198.51.100.42, 10.0.0.2");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("198.51.100.42", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Mitigates client IP spoofing in X-Forwarded-For chain by taking first untrusted from right")
    void shouldIgnoreSpoofedClientIpOnLeft() {
        // Attacker (203.0.113.99) sends X-Forwarded-For: 1.1.1.1
        // Trusted proxy (10.0.0.1) appends attacker's IP: "1.1.1.1, 203.0.113.99"
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "1.1.1.1, 203.0.113.99");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("203.0.113.99", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Resolves IPv6 client address correctly")
    void shouldResolveIpv6Address() {
        ClientIpResolver resolver = new ClientIpResolver(true, "::1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "2001:db8:85a3::8a2e:370:7334");
        request.setRemoteAddr("::1");

        assertEquals("2001:db8:85a3::8a2e:370:7334", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Resolves from X-Real-IP when X-Forwarded-For is absent and caller is trusted proxy")
    void shouldResolveFromXRealIpWhenTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_REAL_IP, "192.0.2.1");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("192.0.2.1", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Rejects malformed header injection and safely falls back to remoteAddr")
    void shouldRejectMalformedHeaderInjection() {
        ClientIpResolver resolver = new ClientIpResolver(true, "10.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "malicious<script>alert(1)</script>; rm -rf /");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("10.0.0.1", resolver.resolveClientIp(request));
    }

    @Test
    @DisplayName("Returns unknown when request is null or remoteAddr is invalid")
    void shouldReturnUnknownForNullOrInvalidRequest() {
        ClientIpResolver resolver = new ClientIpResolver(false, DEFAULT_PROXIES);
        assertEquals("unknown", resolver.resolveClientIp(null));

        MockHttpServletRequest invalidRequest = new MockHttpServletRequest();
        invalidRequest.setRemoteAddr("not-a-valid-ip");
        assertEquals("unknown", resolver.resolveClientIp(invalidRequest));
    }
}
