package com.autoparts.erp.expense.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryUpdateRequest(

    @NotBlank(message = "Expense category name is required")
    @Size(max = 100, message = "Expense category name cannot exceed 100 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9 .,&()\\-]+$",
        message = "Expense category name contains invalid characters"
    )
    String name,

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description
) {
}