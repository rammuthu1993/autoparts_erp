
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

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.AuthenticatedUser;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.service.CashRegisterService;
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

@Service
@Transactional(readOnly = true)
public class PurchasePaymentServiceImpl
        implements PurchasePaymentService {

    private static final Logger log =
        LoggerFactory.getLogger(PurchasePaymentServiceImpl.class);

    private static final int MONEY_SCALE = 2;

    private final PurchasePaymentRepository repository;
    private final CurrentUserService currentUserService;
    private final CashRegisterService cashRegisterService;

    public PurchasePaymentServiceImpl(
            PurchasePaymentRepository repository,
            CurrentUserService currentUserService,
            CashRegisterService cashRegisterService
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.cashRegisterService = cashRegisterService;
    }

    @Override
    @Transactional
    @AdminOrManager
    public PurchasePaymentSummaryResponse recordPayment(
            UUID purchaseUuid,
            PurchasePaymentRequest request
    ) {

        log.info(
            "Purchase payment recording started. Purchase UUID: {}",
            purchaseUuid
        );

        // 1. Lock purchase to prevent concurrent overpayments.

        PurchasePaymentContext purchase =
            repository.findPurchaseForUpdate(purchaseUuid)
                .orElseThrow(() ->
                    new ApiException(
                        PurchaseErrorCode.PURCHASE_NOT_FOUND
                    )
                );

        // 2. Validate purchase status.

        if (!"RECEIVED".equals(purchase.status())) {
            throw new ApiException(
                PurchaseErrorCode.PURCHASE_NOT_RECEIVED_FOR_PAYMENT
            );
        }

        // 3. Validate payment amount.

        BigDecimal paymentAmount = money(request.amount());

        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                PurchaseErrorCode.INVALID_PURCHASE_PAYMENT
            );
        }

        // 4. Calculate existing paid amount.

        BigDecimal paidAmount = money(
            repository.getTotalPaid(purchase.purchaseId())
        );

        // 5. Calculate outstanding balance.

        BigDecimal balanceAmount = money(
            purchase.grandTotal().subtract(paidAmount)
        );

        // 6. Prevent overpayment.

        if (paymentAmount.compareTo(balanceAmount) > 0) {

            log.warn(
                "Purchase payment exceeds outstanding balance. Purchase UUID: {}",
                purchaseUuid
            );

            throw new ApiException(
                PurchaseErrorCode.PURCHASE_PAYMENT_EXCEEDS_BALANCE
            );
        }

        // 7. Get authenticated user.

        Long currentUserId =
            currentUserService.getCurrentUserId();

        // 8. Create payment.

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

        // 9. Save payment.

        PurchasePayment savedPayment =
            repository.create(payment);

        // 10. Update Cash Register only for CASH payments.

        if ("CASH".equals(request.paymentMethod().name())) {

            cashRegisterService.recordTransactionMovement(
                CashMovementType.OUT,
                CashMovementSourceType.PURCHASE_PAYMENT,
                savedPayment.uuid(),
                savedPayment.amount(),
                "Cash paid to supplier for purchase "
                    + purchase.purchaseNumber()
            );

            log.info(
                "Cash register OUT movement recorded. Payment UUID: {}, Amount: {}",
                savedPayment.uuid(),
                savedPayment.amount()
            );
        }

        log.info(
            "Purchase payment recorded successfully. Purchase UUID: {}, Payment UUID: {}",
            purchaseUuid,
            savedPayment.uuid()
        );

        // 11. Return updated payment summary.

        return buildSummary(purchase);
    }

    @Override
    @AuthenticatedUser
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
    @AuthenticatedUser
    public PurchasePaymentResponse findPaymentByUuid(
            UUID paymentUuid
    ) {

        PurchasePayment payment =
            repository.findByUuid(paymentUuid)
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

        PurchasePaymentStatus status =
            calculateStatus(
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
