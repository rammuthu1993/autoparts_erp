package com.autoparts.erp.purchase.returns.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReturnProductResponse(
    UUID uuid,
    UUID purchaseProductUuid,
    Long productId,
    String productCode,
    String productName,
    BigDecimal quantity,
    BigDecimal purchasePrice,
    BigDecimal discountAmount,
    BigDecimal taxPercent,
    BigDecimal taxAmount,
    BigDecimal lineTotal
) {}