package com.autoparts.erp.sales.dto;

import com.autoparts.erp.sales.enums.CancellationRefundStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalesCancellationRefundConfirmRequest(
    @NotNull(message = "Refund status is required")
    CancellationRefundStatus status,
    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    String referenceNumber,
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes
) {}
