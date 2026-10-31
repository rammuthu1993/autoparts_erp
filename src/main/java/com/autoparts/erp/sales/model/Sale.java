package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;

public record Sale(
    Long id,
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}