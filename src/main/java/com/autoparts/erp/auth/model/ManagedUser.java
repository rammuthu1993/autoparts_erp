
package com.autoparts.erp.auth.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ManagedUser(
        Long id,
        UUID uuid,
        String username,
        String email,
        boolean emailVerified,
        boolean enabled,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
    public ManagedUser(
            Long id,
            UUID uuid,
            String username,
            boolean enabled,
            Set<String> roles,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, uuid, username, null, false, enabled, roles, createdAt, updatedAt);
    }
}
