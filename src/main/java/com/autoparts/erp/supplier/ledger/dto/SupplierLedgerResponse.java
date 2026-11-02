package com.autoparts.erp.supplier.ledger.dto;

import java.util.List;

public record SupplierLedgerResponse(
        SupplierLedgerSummary summary,
        List<SupplierLedgerEntry> entries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}