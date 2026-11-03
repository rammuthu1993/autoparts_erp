package com.autoparts.erp.cashregister.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CloseCashSessionRequest(

    @NotNull(message = "Actual closing balance is required")
    @DecimalMin(value = "0.00", message = "Closing balance cannot be negative")
    BigDecimal actualClosingBalance
) {}