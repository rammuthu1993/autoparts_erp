package com.autoparts.erp.masters.category.dto;

import java.util.UUID;

public record CategoryUsageResponse(
        UUID uuid,
        String name,
        boolean active,
        long totalProducts,
        long activeProducts,
        long inactiveProducts
) {}
