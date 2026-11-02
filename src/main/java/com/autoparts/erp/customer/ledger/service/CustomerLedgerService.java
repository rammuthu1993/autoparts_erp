package com.autoparts.erp.customer.ledger.service;

import com.autoparts.erp.customer.ledger.dto.CustomerLedgerResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface CustomerLedgerService {

    CustomerLedgerResponse getCustomerLedger(
            UUID customerUuid,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );
}