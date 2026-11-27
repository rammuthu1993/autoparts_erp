package com.autoparts.erp.inventory.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockReconciliationRequest(
    @NotNull(message = "Physical counted quantity is required")
    @DecimalMin(value = "0.000", message = "Physical quantity cannot be negative")
    BigDecimal physicalQuantity,

    @NotBlank(message = "Reconciliation reason is required")
    @Size(max = 250, message = "Reason must not exceed 250 characters")
    String reason,

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes
) {}
