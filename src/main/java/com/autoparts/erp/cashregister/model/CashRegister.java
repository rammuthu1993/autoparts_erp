package com.autoparts.erp.cashregister.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CashRegister(
    Long id,
    UUID uuid,
    String registerCode,
    String name,
    String description,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}