package com.autoparts.erp.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryStockResponse(
        UUID productUuid,
        String productCode,
        String partNo,
        String productName,
        BigDecimal quantity,
        BigDecimal minimumStock,
        String unit,
        boolean lowStock
) {}