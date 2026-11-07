package com.autoparts.erp.purchase.returns.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierCreditResponse(
    UUID uuid,
    UUID purchaseReturnUuid,
    String creditNoteNumber,
    OffsetDateTime creditDate,
    BigDecimal amount,
    BigDecimal refundedAmount,
    BigDecimal appliedAmount,
    BigDecimal availableAmount
) {}