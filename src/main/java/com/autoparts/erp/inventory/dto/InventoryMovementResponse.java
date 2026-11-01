package com.autoparts.erp.inventory.dto;

import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID uuid,
        UUID productUuid,
        String productCode,
        String productName,
        InventoryMovementType movementType,
        InventoryReferenceType referenceType,
        Long referenceId,
        BigDecimal quantityChange,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter,
        String notes,
        OffsetDateTime createdAt
) {}