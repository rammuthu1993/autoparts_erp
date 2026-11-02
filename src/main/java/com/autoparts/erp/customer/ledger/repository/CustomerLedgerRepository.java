package com.autoparts.erp.customer.ledger.repository;

import com.autoparts.erp.customer.ledger.dto.CustomerLedgerEntry;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerLedgerRepository {

    Optional<CustomerInfo> findCustomerByUuid(UUID customerUuid);

    BigDecimal getOpeningBalance(
            Long customerId,
            LocalDate fromDate
    );

    CustomerLedgerSummary getSummary(
            CustomerInfo customer,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance
    );

    List<CustomerLedgerEntry> findEntries(
            Long customerId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance,
            int limit,
            int offset
    );

    long countEntries(
            Long customerId,
            LocalDate fromDate,
            LocalDate toDate
    );

    record CustomerInfo(
            Long id,
            UUID uuid,
            String customerName
    ) {}
}