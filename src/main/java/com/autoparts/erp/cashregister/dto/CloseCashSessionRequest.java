package com.autoparts.erp.cashregister.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CloseCashSessionRequest(

    @DecimalMin(value = "0.00", message = "Closing balance cannot be negative")
    BigDecimal actualClosingBalance,

    Boolean useDenominations,
    @Min(value = 0, message = "Denomination count cannot be negative") Integer denomination500,
    @Min(value = 0, message = "Denomination count cannot be negative") Integer denomination200,
    @Min(value = 0, message = "Denomination count cannot be negative") Integer denomination100,
    @Min(value = 0, message = "Denomination count cannot be negative") Integer denomination50,
    @Min(value = 0, message = "Denomination count cannot be negative") Integer denomination20,
    @Min(value = 0, message = "Denomination count cannot be negative") Integer denomination10,
    @DecimalMin(value = "0.00", message = "Coins amount cannot be negative") BigDecimal coinsAmount,

    @Size(max = 500, message = "Difference reason must not exceed 500 characters")
    String differenceReason
) {
    public CloseCashSessionRequest(BigDecimal actualClosingBalance) {
        this(actualClosingBalance, false, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, null);
    }
}
