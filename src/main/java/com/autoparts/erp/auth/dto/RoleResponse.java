package com.autoparts.erp.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID uuid,
        String name,
        String description,
        long userCount,
        long permissionCount,
        boolean systemRole,
        Instant createdAt
) {
    public RoleResponse(
            UUID uuid,
            String name,
            String description,
            Instant createdAt
    ) {
        this(uuid, name, description, 0L, 0L, true, createdAt);
    }
}
