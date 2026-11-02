package com.autoparts.erp.expense.management.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Expense(
    Long id,
    UUID uuid,
    Long categoryId,
    String categoryName,
    LocalDate expenseDate,
    String title,
    String description,
    BigDecimal amount,
    ExpensePaymentMethod paymentMethod,
    String referenceNumber,
    String notes,
    ExpenseStatus status,
    Long createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime cancelledAt
) {
}