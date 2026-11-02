package com.autoparts.erp.expense.category.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseCategory(
    Long id,
    UUID uuid,
    String name,
    String description,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}