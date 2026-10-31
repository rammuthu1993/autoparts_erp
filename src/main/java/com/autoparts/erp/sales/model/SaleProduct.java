package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleProduct(
    Long id,
    UUID uuid,
    Long saleId,
    Long productId,
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