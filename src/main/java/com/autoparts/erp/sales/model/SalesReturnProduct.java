package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SalesReturnProduct(
    Long id,
    UUID uuid,
    Long salesReturnId,
    Long salesProductId,
    Long productId,
    UUID productUuid,
    UUID salesProductUuid,
    String productCode,
    String productName,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal discountAmount,
    BigDecimal taxableAmount,
    String hsnCode,
    BigDecimal gstRate,
    BigDecimal cgstRate,
    BigDecimal sgstRate,
    BigDecimal igstRate,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal totalTaxAmount,
    BigDecimal lineTotal,
    OffsetDateTime createdAt
) {}