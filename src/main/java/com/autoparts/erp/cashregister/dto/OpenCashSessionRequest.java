package com.autoparts.erp.cashregister.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpenCashSessionRequest(

    @NotNull(message = "Opening balance is required")
    @DecimalMin(value = "0.00", message = "Opening balance cannot be negative")
    BigDecimal openingBalance,

    @Size(max = 500, message = "Opening notes must not exceed 500 characters")
    String openingNotes
) {
    public OpenCashSessionRequest(BigDecimal openingBalance) {
        this(openingBalance, null);
    }
}
