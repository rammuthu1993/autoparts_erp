package com.autoparts.erp.cashregister.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.cashregister.enums.CashSessionStatus;

public record CashRegisterSession(
    Long id,
    UUID uuid,
    Long cashRegisterId,
    String registerName,
    Long openedBy,
    OffsetDateTime openedAt,
    BigDecimal openingBalance,
    Long closedBy,
    OffsetDateTime closedAt,
    BigDecimal expectedClosingBalance,
    BigDecimal actualClosingBalance,
    BigDecimal difference,
    CashSessionStatus status
) {}