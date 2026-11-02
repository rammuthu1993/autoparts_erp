package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.SalesReturnStatus;

public record SalesReturn(
    Long id,
    UUID uuid,
    Long saleId,
    OffsetDateTime returnDate,
    String reason,
    String notes,
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal taxableAmount,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal totalTaxAmount,
    BigDecimal totalReturnAmount,
    SalesReturnStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}