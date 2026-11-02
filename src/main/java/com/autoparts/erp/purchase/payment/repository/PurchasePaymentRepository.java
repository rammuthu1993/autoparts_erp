package com.autoparts.erp.purchase.payment.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.purchase.model.PurchasePayment;

public interface PurchasePaymentRepository {

    Optional<PurchasePaymentContext> findPurchaseForUpdate(
        UUID purchaseUuid
    );

    BigDecimal getTotalPaid(Long purchaseId);

    PurchasePayment create(PurchasePayment payment);

    List<PurchasePayment> findByPurchaseId(Long purchaseId);

    Optional<PurchasePayment> findByUuid(UUID paymentUuid);

    public record PurchasePaymentContext(
        Long purchaseId,
        UUID purchaseUuid,
        String purchaseNumber,
        String status,
        BigDecimal grandTotal
    ) {}
    
    Optional<PurchasePaymentContext> findPurchaseByUuid(UUID purchaseUuid);
}