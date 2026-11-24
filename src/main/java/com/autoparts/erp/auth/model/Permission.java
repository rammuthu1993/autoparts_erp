package com.autoparts.erp.auth.model;

import java.time.Instant;
import java.util.UUID;

public record Permission(
        Long id,
        UUID uuid,
        String code,
        String name,
        String description,
        String module,
        String action,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
