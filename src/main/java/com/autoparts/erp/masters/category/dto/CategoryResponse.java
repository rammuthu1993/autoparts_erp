package com.autoparts.erp.masters.category.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        Long id,
        UUID uuid,
        String code,
        String name,
        String description,
        Long parentId,
        UUID parentUuid,
        String parentName,
        long productCount,
        long activeProductCount,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public CategoryResponse(
            Long id,
            UUID uuid,
            String name,
            String description,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, uuid, null, name, description, null, null, null, 0L, 0L, active, createdAt, updatedAt);
    }
}
