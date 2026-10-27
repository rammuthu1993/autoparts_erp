package com.autoparts.erp.masters.category.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        Long id,
        UUID uuid,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}