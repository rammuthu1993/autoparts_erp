package com.autoparts.erp.supplier.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierLedgerEntry(
        UUID transactionUuid,
        String transactionType,
        String referenceNumber,
        LocalDate transactionDate,
        BigDecimal purchaseAmount,
        BigDecimal paymentAmount,
        BigDecimal purchaseReturnAmount,
        BigDecimal balance
) {}