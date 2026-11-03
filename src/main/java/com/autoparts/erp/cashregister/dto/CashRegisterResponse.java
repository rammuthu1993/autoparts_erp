package com.autoparts.erp.cashregister.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CashRegisterResponse(
    UUID uuid,
    String registerCode,
    String name,
    String description,
    boolean active,
    OffsetDateTime createdAt
) {}