package com.autoparts.erp.purchase.payment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.purchase.enums.PurchasePaymentStatus;

public record PurchasePaymentSummaryResponse(

    UUID purchaseUuid,
    String purchaseNumber,
    BigDecimal grandTotal,
    BigDecimal paidAmount,
    BigDecimal balanceAmount,
    PurchasePaymentStatus paymentStatus,
    List<PurchasePaymentResponse> payments

) {}