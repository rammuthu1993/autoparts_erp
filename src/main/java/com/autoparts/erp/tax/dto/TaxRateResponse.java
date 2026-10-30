
package com.autoparts.erp.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaxRateResponse(
    UUID uuid,
    String taxName,
    BigDecimal gstRate,
    BigDecimal cgstRate,
    BigDecimal sgstRate,
    BigDecimal igstRate,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
