package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.sales.enums.CancellationRefundStatus;
import com.autoparts.erp.sales.enums.GstAdjustmentType;
import com.autoparts.erp.sales.enums.PaymentMethod;

public record SalesCancellationResponse(
    UUID uuid,
    UUID saleUuid,
    String invoiceNumber,
    String cancellationReason,
    Long cancelledBy,
    String cancelledByUsername,
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
    BigDecimal receivableAdjustmentAmount,
    GstAdjustmentType gstAdjustmentType,
    String gstAdjustmentReference,
    String creditNoteNumber,
    String irn,
    String irnStatus,
    String ewayBillNumber,
    String ewayBillStatus,
    String notes,
    List<ItemResponse> items,
    List<RefundResponse> refunds
) {
    public record ItemResponse(
        UUID uuid,
        UUID productUuid,
        String productCode,
        String productName,
        BigDecimal originalQuantity,
        BigDecimal previouslyReturnedQuantity,
        BigDecimal restoredQuantity,
        String unit,
        BigDecimal taxableReversed,
        BigDecimal cgstReversed,
        BigDecimal sgstReversed,
        BigDecimal igstReversed,
        BigDecimal totalTaxReversed,
        BigDecimal lineTotalReversed
    ) {}

    public record RefundResponse(
        UUID uuid,
        UUID originalPaymentUuid,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        CancellationRefundStatus status,
        String referenceNumber,
        OffsetDateTime refundDate,
        OffsetDateTime confirmedAt,
        String notes
    ) {}
}
