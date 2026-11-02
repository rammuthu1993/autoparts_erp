package com.autoparts.erp.expense.ledger.dto;

import java.util.List;

public record ExpenseLedgerResponse(
    ExpenseLedgerSummary summary,
    List<ExpenseLedgerEntry> entries,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}