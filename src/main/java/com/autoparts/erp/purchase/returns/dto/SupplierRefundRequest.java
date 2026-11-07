package com.autoparts.erp.purchase.returns.dto;

import com.autoparts.erp.purchase.returns.enums.SupplierRefundPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SupplierRefundRequest(

    @NotNull(message = "Payment method is required")
    SupplierRefundPaymentMethod paymentMethod,

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    BigDecimal amount,

    @Size(max = 100, message = "Reference number cannot exceed 100 characters")
    String referenceNumber,

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    String notes

) {}