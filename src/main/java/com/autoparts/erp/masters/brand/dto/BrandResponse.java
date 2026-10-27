package com.autoparts.erp.masters.brand.dto;

import java.time.OffsetDateTime;

public record BrandResponse(
        Long id,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}