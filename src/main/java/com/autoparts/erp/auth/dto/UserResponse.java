
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
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public UserResponse(
            UUID uuid,
            String username,
            boolean enabled,
            Set<String> roles,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(uuid, username, null, false, enabled, roles, createdAt, updatedAt);
    }
}
