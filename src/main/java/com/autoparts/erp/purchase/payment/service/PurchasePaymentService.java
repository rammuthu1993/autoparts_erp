package com.autoparts.erp.purchase.payment.service;

import java.util.UUID;

import com.autoparts.erp.purchase.payment.dto.PurchasePaymentRequest;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentResponse;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentSummaryResponse;

public interface PurchasePaymentService {

    PurchasePaymentSummaryResponse recordPayment(
        UUID purchaseUuid,
        PurchasePaymentRequest request
    );

    PurchasePaymentSummaryResponse findPaymentsByPurchase(
        UUID purchaseUuid
    );

    PurchasePaymentResponse findPaymentByUuid(
        UUID paymentUuid
    );
}