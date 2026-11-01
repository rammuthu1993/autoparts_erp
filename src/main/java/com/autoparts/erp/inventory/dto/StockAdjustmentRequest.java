package com.autoparts.erp.inventory.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockAdjustmentRequest(

        @NotNull(message = "Quantity change is required")
        @Digits(
                integer = 9,
                fraction = 3,
                message = "Quantity change supports up to 3 decimal places"
        )
        BigDecimal quantityChange,

        @NotBlank(message = "Adjustment reason is required")
        @Size(
                max = 250,
                message = "Adjustment reason cannot exceed 250 characters"
        )
        String reason,

        @Size(
                max = 500,
                message = "Notes cannot exceed 500 characters"
        )
        String notes
) {}