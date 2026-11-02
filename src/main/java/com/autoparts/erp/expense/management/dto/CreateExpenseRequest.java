package com.autoparts.erp.expense.management.dto;

import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateExpenseRequest(

    @NotNull(message = "Expense category is required")
    UUID categoryUuid,

    @NotNull(message = "Expense date is required")
    LocalDate expenseDate,

    @NotBlank(message = "Expense title is required")
    @Size(max = 200, message = "Expense title cannot exceed 200 characters")
    String title,

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description,

    @NotNull(message = "Expense amount is required")
    @DecimalMin(
        value = "0.01",
        message = "Expense amount must be greater than zero"
    )
    BigDecimal amount,

    @NotNull(message = "Payment method is required")
    ExpensePaymentMethod paymentMethod,

    @Size(max = 100, message = "Reference number cannot exceed 100 characters")
    String referenceNumber,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes
) {
}