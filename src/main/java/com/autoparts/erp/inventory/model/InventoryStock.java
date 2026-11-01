package com.autoparts.erp.inventory.model;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryStock(
        Long productId,
        UUID productUuid,
        String productCode,
        String partNo,
        String productName,
        BigDecimal quantity,
        BigDecimal minimumStock,
        String unit
) {}