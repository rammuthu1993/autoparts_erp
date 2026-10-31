package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.autoparts.erp.sales.dto.SalesProductResponse;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;

public record SalesResponse(
    UUID uuid,
    String invoiceNumber,
    String customerName,
    String customerPhone,
    LocalDate saleDate,
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal taxableAmount,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal totalTaxAmount,
    BigDecimal grandTotal,
    BigDecimal paidAmount,
    BigDecimal balanceAmount,
    SalesStatus status,
    SalesPaymentStatus paymentStatus,
    String notes,
    List<SalesProductResponse> products,
    List<SalesPaymentResponse> payments,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}