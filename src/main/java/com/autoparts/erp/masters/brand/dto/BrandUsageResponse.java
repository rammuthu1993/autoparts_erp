package com.autoparts.erp.masters.brand.dto;

import java.util.UUID;

public record BrandUsageResponse(
        UUID uuid,
        String name,
        boolean active,
        long totalProducts,
        long activeProducts,
        long inactiveProducts
) {}
