package com.autoparts.erp.supplier.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierLedgerSummary(
        UUID supplierUuid,
        String supplierName,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal openingBalance,
        BigDecimal totalPurchases,
        BigDecimal totalPayments,
        BigDecimal closingBalance
) {}