package com.autoparts.erp.tax.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductTaxMapping(
    Long id,
    UUID uuid,
    Long productId,
    String productCode,
    String productName,
    UUID taxRateUuid,
    String taxName,
    BigDecimal gstRate,
    BigDecimal cgstRate,
    BigDecimal sgstRate,
    BigDecimal igstRate,
    String hsnCode,
    String hsnDescription,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String changeReason,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
