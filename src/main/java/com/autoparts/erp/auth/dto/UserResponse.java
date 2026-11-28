package com.autoparts.erp.auth.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID uuid,
        String username,
        String email,
        boolean emailVerified,
        boolean enabled,
        int failedLoginAttempts,
        Instant lockedUntil,
        boolean locked,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public UserResponse(
            UUID uuid,
            String username,
            String email,
            boolean emailVerified,
            boolean enabled,
            Set<String> roles,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(uuid, username, email, emailVerified, enabled, 0, null, false, roles, createdAt, updatedAt);
    }

    public UserResponse(
            UUID uuid,
            String username,
            boolean enabled,
            Set<String> roles,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(uuid, username, null, false, enabled, 0, null, false, roles, createdAt, updatedAt);
    }
}
