
package com.autoparts.erp.purchase.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseProductResponse(
    UUID uuid,
    Long productId,
//    String itemId,
    String partNo,
    String productName,
    BigDecimal quantity,
    BigDecimal purchasePrice,
    BigDecimal discountAmount,
    BigDecimal taxPercent,
    BigDecimal taxAmount,
    BigDecimal lineTotal
) {}
