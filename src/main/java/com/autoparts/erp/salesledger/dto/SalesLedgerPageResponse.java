package com.autoparts.erp.salesledger.dto;

import java.util.List;

public record SalesLedgerPageResponse(
    SalesLedgerSummaryResponse summary,
    List<SalesLedgerEntryResponse> entries,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {}