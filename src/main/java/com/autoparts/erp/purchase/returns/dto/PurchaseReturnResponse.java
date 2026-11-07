package com.autoparts.erp.purchase.returns.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseReturnResponse(
    UUID uuid,
    String returnNumber,
    UUID purchaseUuid,
    String purchaseNumber,
    OffsetDateTime returnDate,
    String reason,
    String notes,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal totalReturnAmount,
    List<PurchaseReturnProductResponse> products,
    SupplierCreditResponse supplierCredit,
    OffsetDateTime createdAt
) {}