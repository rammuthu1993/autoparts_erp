package com.autoparts.erp.sales.ledger.repository;

import java.time.LocalDate;
import java.util.List;

import com.autoparts.erp.sales.ledger.dto.SalesLedgerEntryResponse;
import com.autoparts.erp.sales.ledger.dto.SalesLedgerSummaryResponse;

public interface SalesLedgerRepository {

    SalesLedgerSummaryResponse getSummary(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate
    );

    List<SalesLedgerEntryResponse> findEntries(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int limit,
        int offset
    );

    long countEntries(
        Long customerId,
        LocalDate fromDate,
        LocalDate toDate,
        String search
    );
}
