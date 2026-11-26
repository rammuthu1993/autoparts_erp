package com.autoparts.erp.masters.brand.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BrandResponse(
        Long id,
        UUID uuid,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public BrandResponse(UUID uuid, String name, String description, boolean active) {
        this(null, uuid, name, description, active, null, null);
    }
}
