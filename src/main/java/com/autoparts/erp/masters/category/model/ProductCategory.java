package com.autoparts.erp.masters.category.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductCategory(
        Long id,
        UUID uuid,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}