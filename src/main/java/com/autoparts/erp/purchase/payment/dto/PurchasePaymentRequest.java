package com.autoparts.erp.purchase.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.autoparts.erp.purchase.enums.PurchasePaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PurchasePaymentRequest(

    @NotNull(message = "Payment method is required")
    PurchasePaymentMethod paymentMethod,

    @NotNull(message = "Payment amount is required")
    @DecimalMin(
        value = "0.01",
        message = "Payment amount must be greater than zero"
    )
    BigDecimal amount,

    @Size(max = 100, message = "Reference number cannot exceed 100 characters")
    String referenceNumber,

    OffsetDateTime paymentDate,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes

) {}