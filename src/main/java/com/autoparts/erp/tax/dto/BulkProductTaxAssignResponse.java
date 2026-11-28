package com.autoparts.erp.tax.dto;

import java.util.List;

public record BulkProductTaxAssignResponse(
    int totalRequested,
    int succeeded,
    int failed,
    List<BulkProductTaxAssignItemResult> results
) {}
