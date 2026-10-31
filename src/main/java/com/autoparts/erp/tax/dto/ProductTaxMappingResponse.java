
package com.autoparts.erp.tax.dto;

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
    String hsnCode,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    OffsetDateTime createdAt
) {}
