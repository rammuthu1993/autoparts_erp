
package com.autoparts.erp.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        Long id,
        UUID uuid,
        String itemId,
        String partNo,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        BigDecimal purchasePrice,
        BigDecimal sellingPrice,
        int qty,
        BigDecimal minimumStock,
        String unit,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
