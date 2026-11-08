package com.autoparts.erp.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID uuid,
        String name,
        String description,
        Instant createdAt
) {
}