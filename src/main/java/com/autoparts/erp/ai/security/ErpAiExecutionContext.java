package com.autoparts.erp.ai.security;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Immutable security execution context capturing the authenticated identity and authorities
 * strictly on the server-side HTTP request thread.
 *
 * <p>Uses {@link Set#copyOf(Collection)} to guarantee true defensive immutability.
 * Under no circumstances may this context be populated or modified from user chat prompts,
 * model-generated state, or LLM responses.</p>
 */
public record ErpAiExecutionContext(
        UUID conversationId,
        String userSubject,
        Set<String> authorities,
        Instant requestTimestamp
) {

    public ErpAiExecutionContext {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (userSubject == null || userSubject.isBlank()) {
            throw new IllegalArgumentException("userSubject must not be null or blank");
        }
        // Defensive copying guarantees callers cannot mutate authorities via retained references
        authorities = (authorities == null) ? Set.of() : Set.copyOf(authorities);
        requestTimestamp = (requestTimestamp == null) ? Instant.now() : requestTimestamp;
    }

    /**
     * Factory method creating a validated, immutable context.
     * Safely filters null and blank authority strings, normalizing values.
     */
    public static ErpAiExecutionContext create(
            UUID conversationId,
            String userSubject,
            Collection<String> grantedRoles
    ) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (userSubject == null || userSubject.isBlank()) {
            throw new IllegalArgumentException("userSubject must not be null or blank");
        }
        Objects.requireNonNull(grantedRoles, "grantedRoles must not be null");

        Set<String> normalized = grantedRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Set::copyOf));

        return new ErpAiExecutionContext(conversationId, userSubject.trim(), normalized, Instant.now());
    }

    /**
     * Checks if the context contains the specified authority, supporting both prefixed ("ROLE_ADMIN")
     * and non-prefixed ("ADMIN") format.
     */
    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String trimmed = role.trim();
        String withPrefix = trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
        String withoutPrefix = trimmed.startsWith("ROLE_") ? trimmed.substring(5) : trimmed;
        return authorities.contains(trimmed) || authorities.contains(withPrefix) || authorities.contains(withoutPrefix);
    }

    /**
     * Checks if the context contains any of the specified roles.
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        for (String r : roles) {
            if (hasRole(r)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asserts that the authenticated subject has at least one of the specified roles.
     *
     * @throws ToolAuthorizationException if none of the required roles are held
     */
    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new ToolAuthorizationException(
                    "Access denied. Required one of roles: " + Arrays.toString(roles) +
                    ", but granted authorities: " + authorities
            );
        }
    }

    /**
     * Convenience helper matching {@code @SalesAccess} (Cashier, Manager, Admin).
     */
    public boolean isSalesAccess() {
        return hasAnyRole("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER");
    }

    /**
     * Convenience helper matching {@code @AdminOrManager} (Manager, Admin).
     */
    public boolean isAdminOrManager() {
        return hasAnyRole("ROLE_ADMIN", "ROLE_MANAGER");
    }

    /**
     * Convenience helper matching {@code @AdminOnly} (Admin).
     */
    public boolean isAdminOnly() {
        return hasRole("ROLE_ADMIN");
    }
}
