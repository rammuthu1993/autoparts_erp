
package com.autoparts.erp.tax.model;

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
    String hsnCode,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    OffsetDateTime createdAt
) {}
