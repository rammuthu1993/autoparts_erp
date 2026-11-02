package com.autoparts.erp.sales.ledger.dto;

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