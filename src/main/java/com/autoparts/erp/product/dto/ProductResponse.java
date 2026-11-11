package com.autoparts.erp.product.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        Long id,
        UUID uuid,
        String productCode,
        String partNo,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        BigDecimal purchasePrice,
        BigDecimal sellingPrice,
        BigDecimal minimumStock,
        String unit,
        String barcode,
        String barcodeType,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
