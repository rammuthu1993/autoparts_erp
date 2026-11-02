package com.autoparts.erp.sales.ledger.repository;

import java.time.LocalDate;
import java.util.List;

import com.autoparts.erp.sales.ledger.dto.SalesLedgerEntryResponse;
import com.autoparts.erp.sales.ledger.dto.SalesLedgerSummaryResponse;

public interface SalesLedgerRepository {

    SalesLedgerSummaryResponse getSummary(
        LocalDate fromDate,
        LocalDate toDate,
        String search
    );

    List<SalesLedgerEntryResponse> findEntries(
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int limit,
        int offset
    );

    long countEntries(
        LocalDate fromDate,
        LocalDate toDate,
        String search
    );
}