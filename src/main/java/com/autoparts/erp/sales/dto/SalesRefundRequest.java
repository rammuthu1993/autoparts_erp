package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;

import com.autoparts.erp.sales.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalesRefundRequest(

    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod,

    @NotNull(message = "Refund amount is required")
    @DecimalMin(
        value = "0.01",
        message = "Refund amount must be greater than zero"
    )
    BigDecimal amount,

    @Size(
        max = 100,
        message = "Reference number cannot exceed 100 characters"
    )
    String referenceNumber,

    @Size(
        max = 500,
        message = "Notes cannot exceed 500 characters"
    )
    String notes

) {}