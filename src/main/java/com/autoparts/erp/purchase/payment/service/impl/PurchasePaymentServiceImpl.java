package com.autoparts.erp.purchase.payment.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.purchase.exception.PurchaseErrorCode;
import com.autoparts.erp.purchase.model.PurchasePayment;
import com.autoparts.erp.purchase.enums.PurchasePaymentStatus;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentRequest;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentResponse;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentSummaryResponse;
import com.autoparts.erp.purchase.payment.repository.PurchasePaymentRepository;
import com.autoparts.erp.purchase.payment.repository.PurchasePaymentRepository.PurchasePaymentContext;
import com.autoparts.erp.purchase.payment.service.PurchasePaymentService;
import com.autoparts.erp.auth.security.CurrentUserService;

@Service
@Transactional(readOnly = true)
public class PurchasePaymentServiceImpl
        implements PurchasePaymentService {

    private static final Logger log =
        LoggerFactory.getLogger(PurchasePaymentServiceImpl.class);

    private static final int MONEY_SCALE = 2;

    private final PurchasePaymentRepository repository;
    private final CurrentUserService currentUserService;

    public PurchasePaymentServiceImpl(
            PurchasePaymentRepository repository,
            CurrentUserService currentUserService
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional
    public PurchasePaymentSummaryResponse recordPayment(
            UUID purchaseUuid,
            PurchasePaymentRequest request
    ) {

        // Lock the purchase row to prevent concurrent overpayments.
        PurchasePaymentContext purchase =
            repository.findPurchaseForUpdate(purchaseUuid)
                .orElseThrow(() ->
                    new ApiException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND
                    )
                );

        if (!"RECEIVED".equals(purchase.status())) {
            throw new ApiException(
                PurchaseErrorCode.PURCHASE_NOT_RECEIVED_FOR_PAYMENT
            );
        }

        BigDecimal paymentAmount = money(request.amount());

        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                PurchaseErrorCode.INVALID_PURCHASE_PAYMENT
            );
        }

        BigDecimal paidAmount = money(
            repository.getTotalPaid(purchase.purchaseId())
        );

        BigDecimal balanceAmount = money(
            purchase.grandTotal().subtract(paidAmount)
        );

        if (paymentAmount.compareTo(balanceAmount) > 0) {
            log.warn(
                "Purchase payment rejected because amount exceeds outstanding balance. Purchase UUID: {}",
                purchaseUuid
            );

            throw new ApiException(
                PurchaseErrorCode.PURCHASE_PAYMENT_EXCEEDS_BALANCE
            );
        }

        Long currentUserId = currentUserService.getCurrentUserId();

        PurchasePayment payment = new PurchasePayment(
            null,
            UUID.randomUUID(),
            purchase.purchaseId(),
            request.paymentMethod(),
            paymentAmount,
            clean(request.referenceNumber()),
            request.paymentDate() == null
                ? OffsetDateTime.now()
                : request.paymentDate(),
            clean(request.notes()),
            currentUserId,
            null
        );

        repository.create(payment);

        log.info(
            "Purchase payment recorded successfully. Purchase UUID: {}, Payment UUID: {}",
            purchaseUuid,
            payment.uuid()
        );

        return buildSummary(purchase);
    }

    @Override
    public PurchasePaymentSummaryResponse findPaymentsByPurchase(
            UUID purchaseUuid
    ) {

        PurchasePaymentContext purchase =
            repository.findPurchaseByUuid(purchaseUuid)
                .orElseThrow(() ->
                    new ApiException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND
                    )
                );

        return buildSummary(purchase);
    }

    @Override
    public PurchasePaymentResponse findPaymentByUuid(
            UUID paymentUuid
    ) {

        PurchasePayment payment = repository.findByUuid(paymentUuid)
            .orElseThrow(() ->
                new ApiException(
                    PurchaseErrorCode.PURCHASE_PAYMENT_NOT_FOUND
                )
            );

        return toResponse(payment);
    }

    private PurchasePaymentSummaryResponse buildSummary(
            PurchasePaymentContext purchase
    ) {

        BigDecimal paidAmount = money(
            repository.getTotalPaid(purchase.purchaseId())
        );

        BigDecimal balanceAmount = money(
            purchase.grandTotal().subtract(paidAmount)
        );

        PurchasePaymentStatus status = calculateStatus(
            paidAmount,
            purchase.grandTotal()
        );

        List<PurchasePaymentResponse> payments =
            repository.findByPurchaseId(purchase.purchaseId())
                .stream()
                .map(this::toResponse)
                .toList();

        return new PurchasePaymentSummaryResponse(
            purchase.purchaseUuid(),
            purchase.purchaseNumber(),
            money(purchase.grandTotal()),
            paidAmount,
            balanceAmount,
            status,
            payments
        );
    }

    private PurchasePaymentStatus calculateStatus(
            BigDecimal paidAmount,
            BigDecimal grandTotal
    ) {

        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            return PurchasePaymentStatus.UNPAID;
        }

        if (paidAmount.compareTo(grandTotal) == 0) {
            return PurchasePaymentStatus.PAID;
        }

        return PurchasePaymentStatus.PARTIALLY_PAID;
    }

    private PurchasePaymentResponse toResponse(
            PurchasePayment payment
    ) {

        return new PurchasePaymentResponse(
            payment.uuid(),
            payment.paymentMethod(),
            payment.amount(),
            payment.referenceNumber(),
            payment.paymentDate(),
            payment.notes(),
            payment.createdAt()
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank()
            ? null
            : value.trim();
    }
}