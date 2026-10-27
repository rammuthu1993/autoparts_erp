package com.autoparts.erp.masters.brand.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductBrand(
        Long id,
        UUID uuid,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}