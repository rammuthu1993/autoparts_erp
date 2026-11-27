package com.autoparts.erp.sales.dto;

import java.util.List;

import com.autoparts.erp.sales.enums.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SalesCancelRequest(
    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    String reason,
    PaymentMethod refundMethod,
    @Size(max = 100, message = "Refund reference must not exceed 100 characters")
    String refundReference,
    @Valid
    List<SalesCancellationRefundAllocation> refundAllocations,
    Boolean confirmExternalRefund,
    @Size(max = 100, message = "GST adjustment reference must not exceed 100 characters")
    String gstAdjustmentReference,
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    String idempotencyKey,
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    String notes
) {
    public SalesCancelRequest(String reason) {
        this(reason, null, null, null, null, null, null, null);
    }
}
