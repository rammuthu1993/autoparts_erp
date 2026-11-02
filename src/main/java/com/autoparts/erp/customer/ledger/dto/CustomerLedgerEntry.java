package com.autoparts.erp.customer.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerLedgerEntry(
        UUID transactionUuid,
        String transactionType,
        String referenceNumber,
        LocalDate transactionDate,
        BigDecimal salesAmount,
        BigDecimal paymentAmount,
        BigDecimal balance
) {}