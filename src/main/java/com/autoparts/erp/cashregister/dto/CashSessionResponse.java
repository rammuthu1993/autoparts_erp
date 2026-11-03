package com.autoparts.erp.cashregister.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.cashregister.enums.CashSessionStatus;

public record CashSessionResponse(
    UUID uuid,
    UUID registerUuid,
    String registerName,
    BigDecimal openingBalance,
    BigDecimal totalCashIn,
    BigDecimal totalCashOut,
    BigDecimal expectedClosingBalance,
    BigDecimal actualClosingBalance,
    BigDecimal difference,
    CashSessionStatus status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt
) {}