package com.autoparts.erp.sales.ledger.service;

import java.time.LocalDate;

import com.autoparts.erp.sales.ledger.dto.SalesLedgerPageResponse;

public interface SalesLedgerService {

    SalesLedgerPageResponse getSalesLedger(
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int page,
        int size
    );
}