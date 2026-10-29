
package com.autoparts.erp.purchase.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseProduct(
    Long id,
    UUID uuid,
    Long productId,
    String itemId,
    String partNo,
    String productName,
    BigDecimal quantity,
    BigDecimal purchasePrice,
    BigDecimal discountAmount,
    BigDecimal taxPercent,
    BigDecimal taxAmount,
    BigDecimal lineTotal
) {}
