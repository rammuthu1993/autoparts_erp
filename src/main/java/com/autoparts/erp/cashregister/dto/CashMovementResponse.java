package com.autoparts.erp.cashregister.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;

public record CashMovementResponse(
    UUID uuid,
    CashMovementType movementType,
    CashMovementSourceType sourceType,
    UUID sourceUuid,
    BigDecimal amount,
    String description,
    OffsetDateTime createdAt
) {}