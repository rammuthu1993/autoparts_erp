package com.autoparts.erp.expense.ledger.dto;

import java.math.BigDecimal;

public record ExpenseLedgerSummary(
    long totalExpenses,
    BigDecimal totalAmount
) {
}