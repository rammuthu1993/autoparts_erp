package com.autoparts.erp.expense.ledger.dto;

import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseLedgerEntry(
    UUID expenseUuid,
    LocalDate expenseDate,
    String title,
    String categoryName,
    BigDecimal amount,
    ExpensePaymentMethod paymentMethod,
    String referenceNumber,
    String status
) {
}