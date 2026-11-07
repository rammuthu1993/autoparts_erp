package com.autoparts.erp.purchase.returns.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierCredit(
    Long id,
    UUID uuid,
    Long purchaseReturnId,
    String creditNoteNumber,
    OffsetDateTime creditDate,
    BigDecimal amount,
    BigDecimal refundedAmount,
    BigDecimal appliedAmount,
    BigDecimal availableAmount,
    OffsetDateTime createdAt
) {}