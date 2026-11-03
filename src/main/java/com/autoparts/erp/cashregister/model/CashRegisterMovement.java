package com.autoparts.erp.cashregister.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;

public record CashRegisterMovement(
    Long id,
    UUID uuid,
    Long sessionId,
    CashMovementType movementType,
    CashMovementSourceType sourceType,
    UUID sourceUuid,
    BigDecimal amount,
    String description,
    Long createdBy,
    OffsetDateTime createdAt
) {}