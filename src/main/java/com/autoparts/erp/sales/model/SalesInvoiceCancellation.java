package com.autoparts.erp.sales.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.sales.enums.CancellationRefundStatus;
import com.autoparts.erp.sales.enums.GstAdjustmentType;
import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesInvoiceCancellation(
    Long id,
    UUID uuid,
    Long saleId,
    String invoiceNumber,
    String idempotencyKey,
    String cancellationReason,
    Long cancelledBy,
    OffsetDateTime cancelledAt,
    BigDecimal originalGrandTotal,
    BigDecimal originalTaxableAmount,
    BigDecimal originalCgstAmount,
    BigDecimal originalSgstAmount,
    BigDecimal originalIgstAmount,
    BigDecimal originalTotalTaxAmount,
    BigDecimal previouslyPaidAmount,
    BigDecimal previouslyReturnedAmount,
    BigDecimal previouslyRefundedAmount,
    BigDecimal cancelledInvoiceAmount,
    BigDecimal refundAmount,
    PaymentMethod refundMethod,
    CancellationRefundStatus refundStatus,
    String refundReference,
    OffsetDateTime refundConfirmedAt,
    Long refundConfirmedBy,
    BigDecimal receivableAdjustmentAmount,
    GstAdjustmentType gstAdjustmentType,
    String gstAdjustmentReference,
    String creditNoteNumber,
    String irn,
    String irnStatus,
    String ewayBillNumber,
    String ewayBillStatus,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
