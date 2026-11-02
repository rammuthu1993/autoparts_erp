package com.autoparts.erp.salesledger.service;

import java.time.LocalDate;

import com.autoparts.erp.salesledger.dto.SalesLedgerPageResponse;

public interface SalesLedgerService {

    SalesLedgerPageResponse getSalesLedger(
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int page,
        int size
    );
}