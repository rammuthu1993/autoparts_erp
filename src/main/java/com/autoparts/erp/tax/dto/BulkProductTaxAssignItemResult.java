package com.autoparts.erp.tax.dto;

import java.util.UUID;

public record BulkProductTaxAssignItemResult(
    UUID productUuid,
    String productCode,
    String productName,
    boolean success,
    String actionTaken,
    String message,
    UUID mappingUuid
) {}
