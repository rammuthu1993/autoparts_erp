package com.autoparts.erp.supplier.ledger.repository;

import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerEntry;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierLedgerRepository {

    Optional<SupplierInfo> findSupplierByUuid(UUID supplierUuid);

    BigDecimal getOpeningBalance(
            Long supplierId,
            LocalDate fromDate
    );

    SupplierLedgerSummary getSummary(
            SupplierInfo supplier,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance
    );

    List<SupplierLedgerEntry> findEntries(
            Long supplierId,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal openingBalance,
            int limit,
            int offset
    );

    long countEntries(
            Long supplierId,
            LocalDate fromDate,
            LocalDate toDate
    );

    record SupplierInfo(
            Long id,
            UUID uuid,
            String supplierName
    ) {}
}