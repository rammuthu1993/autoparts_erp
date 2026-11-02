package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.sales.enums.SalesReturnStatus;

public record SalesReturnResponse(
    UUID uuid,
    UUID saleUuid,
    String invoiceNumber,
    OffsetDateTime returnDate,
    String reason,
    String notes,
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal taxableAmount,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal totalTaxAmount,
    BigDecimal totalReturnAmount,
    BigDecimal refundedAmount,
    BigDecimal refundableAmount,
    SalesReturnStatus status,
    List<SalesReturnProductResponse> products,
    List<SalesRefundResponse> refunds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}