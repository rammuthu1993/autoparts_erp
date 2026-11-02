package com.autoparts.erp.expense.category.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseCategoryResponse(
    Long id,
    UUID uuid,
    String name,
    String description,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}