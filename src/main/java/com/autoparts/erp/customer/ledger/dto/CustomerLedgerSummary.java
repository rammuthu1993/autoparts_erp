package com.autoparts.erp.customer.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerLedgerSummary(
        UUID customerUuid,
        String customerName,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal openingBalance,
        BigDecimal totalSales,
        BigDecimal totalPayments,
        BigDecimal closingBalance
) {}