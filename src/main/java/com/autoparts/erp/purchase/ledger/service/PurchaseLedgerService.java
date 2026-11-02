package com.autoparts.erp.purchase.ledger.service;

import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface PurchaseLedgerService {

    PurchaseLedgerResponse getLedger(
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierUuid,
        String search,
        int page,
        int size
    );
}