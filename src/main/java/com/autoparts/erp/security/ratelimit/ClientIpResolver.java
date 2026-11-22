package com.autoparts.erp.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves the originating client IP address from HTTP servlet requests, safely inspecting
 * reverse proxy headers (X-Forwarded-For, X-Real-IP) and falling back to the socket remote address.
 *
 * <p>Security features:</p>
 * <ul>
 *   <li>Defaults forwarded-header trust to {@code false} to prevent header spoofing when directly exposed.</li>
 *   <li>Only trusts forwarded headers when the direct connecting socket (remoteAddr) belongs to an explicitly configured trusted proxy.</li>
 *   <li>Traverses multi-hop {@code X-Forwarded-For} chains right-to-left to locate the first untrusted upstream IP.</li>
 *   <li>Validates IPv4 and IPv6 syntax strictly via {@link InetAddress#ofLiteral(String)}, preventing DNS lookups and header injections.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_REAL_IP = "X-Real-IP";

    private final boolean trustForwardedHeaders;
    private final Set<InetAddress> trustedProxies;

    @Autowired
    public ClientIpResolver(
            @Value("${app.security.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
            @Value("${app.security.rate-limit.trusted-proxies:127.0.0.1,::1}") String trustedProxiesConfig
    ) {
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.trustedProxies = parseTrustedProxies(trustedProxiesConfig);
    }

    public ClientIpResolver(boolean trustForwardedHeaders) {
        this(trustForwardedHeaders, "127.0.0.1,::1");
    }

    /**
     * Resolves the originating client IP address.
     *
     * @param request the HTTP servlet request
     * @return a safe IP string or "unknown"
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = request.getRemoteAddr();
        String directIp = (remoteAddr != null) ? remoteAddr.trim() : "";
        if (!isValidIp(directIp)) {
            return "unknown";
        }

        // If forwarded headers are not trusted or direct caller is not a trusted proxy,
        // use direct socket IP to prevent spoofing
        if (!trustForwardedHeaders || !isTrustedProxy(directIp)) {
            return directIp;
        }

        // Direct caller is a trusted proxy and trustForwardedHeaders is enabled.
        // Inspect X-Forwarded-For header right-to-left to find first untrusted upstream IP
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                if (isValidIp(candidate)) {
                    if (!isTrustedProxy(candidate)) {
                        return candidate;
                    }
                } else {
                    // Malformed IP in chain - stop traversal to prevent header injection
                    break;
                }
            }
        }

        // Inspect X-Real-IP if no untrusted candidate was resolved from X-Forwarded-For
        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            String candidate = realIp.trim();
            if (isValidIp(candidate) && !isTrustedProxy(candidate)) {
                return candidate;
            }
        }

        return directIp;
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return false;
        }
        if ("unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        try {
            InetAddress.ofLiteral(ip);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.ofLiteral(ip);
            return trustedProxies.contains(address);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private Set<InetAddress> parseTrustedProxies(String config) {
        if (config == null || config.isBlank()) {
            return Collections.emptySet();
        }
        Set<InetAddress> set = new HashSet<>();
        for (String part : config.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    set.add(InetAddress.ofLiteral(trimmed));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid trusted proxy IP literal configured: [{}]", trimmed);
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }
}
