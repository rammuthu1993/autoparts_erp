package com.autoparts.erp.purchase.ledger.repository;

import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerEntry;
import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PurchaseLedgerRepository {

    List<PurchaseLedgerEntry> findEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search,
        int limit,
        long offset
    );

    PurchaseLedgerSummary getSummary(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search
    );

    long countEntries(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search
    );
}