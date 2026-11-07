package com.autoparts.erp.sales.ledger.service;

import java.time.LocalDate;
import java.util.UUID;

import com.autoparts.erp.sales.ledger.dto.SalesLedgerPageResponse;

public interface SalesLedgerService {

    SalesLedgerPageResponse getSalesLedger(
        UUID customerUuid,
        LocalDate fromDate,
        LocalDate toDate,
        String search,
        int page,
        int size
    );
}
