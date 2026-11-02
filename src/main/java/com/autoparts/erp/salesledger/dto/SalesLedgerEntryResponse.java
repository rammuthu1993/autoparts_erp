package com.autoparts.erp.salesledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesLedgerEntryResponse(
    LocalDate transactionDate,
    String transactionType,
    String invoiceNumber,
    String customerName,
    String description,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal runningBalance
) {}