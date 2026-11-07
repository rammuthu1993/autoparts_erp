package com.autoparts.erp.purchase.returns.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReturnProduct(
    Long id,
    UUID uuid,
    Long purchaseReturnId,
    Long purchaseProductId,
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