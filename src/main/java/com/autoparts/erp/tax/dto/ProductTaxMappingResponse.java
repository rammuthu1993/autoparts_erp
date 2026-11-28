package com.autoparts.erp.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductTaxMappingResponse(
    UUID uuid,
    UUID productUuid,
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
    String periodStatus,
    String changeReason,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
