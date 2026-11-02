package com.autoparts.erp.supplier.ledger.service;

import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface SupplierLedgerService {

    SupplierLedgerResponse getSupplierLedger(
            UUID supplierUuid,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );
}