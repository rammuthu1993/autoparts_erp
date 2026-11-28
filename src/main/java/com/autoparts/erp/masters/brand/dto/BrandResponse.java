package com.autoparts.erp.masters.brand.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BrandResponse(
        Long id,
        UUID uuid,
        String code,
        String name,
        String description,
        String countryOfOrigin,
        String website,
        long productCount,
        long activeProductCount,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public BrandResponse(UUID uuid, String name, String description, boolean active) {
        this(null, uuid, null, name, description, null, null, 0L, 0L, active, null, null);
    }

    public BrandResponse(
            Long id,
            UUID uuid,
            String name,
            String description,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, uuid, null, name, description, null, null, 0L, 0L, active, createdAt, updatedAt);
    }
}
