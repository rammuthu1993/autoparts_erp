package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SalesInvoiceCancellationItem(
    Long id,
    UUID uuid,
    Long cancellationId,
    Long salesProductId,
    Long productId,
    String productCode,
    String productName,
    BigDecimal originalQuantity,
    BigDecimal previouslyReturnedQuantity,
    BigDecimal restoredQuantity,
    String unit,
    BigDecimal taxableReversed,
    BigDecimal cgstReversed,
    BigDecimal sgstReversed,
    BigDecimal igstReversed,
    BigDecimal totalTaxReversed,
    BigDecimal lineTotalReversed,
    OffsetDateTime createdAt
) {}
