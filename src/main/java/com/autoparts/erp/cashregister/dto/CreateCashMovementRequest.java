package com.autoparts.erp.cashregister.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCashMovementRequest(

    @NotNull
    CashMovementType movementType,

    @NotNull
    CashMovementSourceType sourceType,

    UUID sourceUuid,

    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal amount,

    @Size(max = 500)
    String description
) {}