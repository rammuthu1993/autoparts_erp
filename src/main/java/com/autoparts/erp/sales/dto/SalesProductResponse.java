package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesProductResponse(
    UUID uuid,
    UUID productUuid,
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
    BigDecimal lineTotal
) {}