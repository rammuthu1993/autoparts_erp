package com.autoparts.erp.purchase.ledger.dto;

import java.util.List;

public record PurchaseLedgerResponse(
    PurchaseLedgerSummary summary,
    List<PurchaseLedgerEntry> entries,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}