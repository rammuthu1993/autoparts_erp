package com.autoparts.erp.purchase.returns.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseReturn(
    Long id,
    UUID uuid,
    String returnNumber,
    Long purchaseId,
    OffsetDateTime returnDate,
    String reason,
    String notes,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal totalReturnAmount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}