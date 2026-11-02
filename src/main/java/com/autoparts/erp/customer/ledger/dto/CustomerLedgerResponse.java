package com.autoparts.erp.customer.ledger.dto;

import java.util.List;

public record CustomerLedgerResponse(
        CustomerLedgerSummary summary,
        List<CustomerLedgerEntry> entries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}