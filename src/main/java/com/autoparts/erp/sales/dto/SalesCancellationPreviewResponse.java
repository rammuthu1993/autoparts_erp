package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.sales.enums.GstAdjustmentType;
import com.autoparts.erp.sales.enums.SalesStatus;

public record SalesCancellationPreviewResponse(
    UUID saleUuid,
    String invoiceNumber,
    LocalDate saleDate,
    String customerName,
    String customerPhone,
    SalesStatus status,
    boolean eligibleForCancellation,
    String ineligibilityReason,
    BigDecimal grandTotal,
    BigDecimal previouslyPaidAmount,
    BigDecimal previouslyReturnedAmount,
    BigDecimal previouslyRefundedAmount,
    BigDecimal outstandingAmount,
    BigDecimal cancelledInvoiceAmount,
    BigDecimal refundableAmount,
    BigDecimal receivableAdjustmentAmount,
    GstAdjustmentType gstAdjustmentType,
    BigDecimal taxableReversed,
    BigDecimal cgstReversed,
    BigDecimal sgstReversed,
    BigDecimal igstReversed,
    BigDecimal totalTaxReversed,
    boolean requiresCashSession,
    boolean dailyPeriodClosed,
    List<SalesPaymentResponse> originalPayments,
    List<PreviewItem> items,
    List<String> warnings
) {
    public record PreviewItem(
        UUID salesProductUuid,
        UUID productUuid,
        String productCode,
        String productName,
        String unit,
        BigDecimal originalQuantity,
        BigDecimal previouslyReturnedQuantity,
        BigDecimal remainingQuantityToRestore,
        BigDecimal taxableReversed,
        BigDecimal totalTaxReversed,
        BigDecimal lineTotalReversed
    ) {}
}
