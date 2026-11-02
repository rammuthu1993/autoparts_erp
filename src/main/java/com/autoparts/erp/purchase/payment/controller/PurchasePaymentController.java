package com.autoparts.erp.purchase.payment.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.purchase.payment.dto.PurchasePaymentRequest;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentResponse;
import com.autoparts.erp.purchase.payment.dto.PurchasePaymentSummaryResponse;
import com.autoparts.erp.purchase.payment.service.PurchasePaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchasePaymentController {

    private final PurchasePaymentService paymentService;

    public PurchasePaymentController(
            PurchasePaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    /**
     * Record a payment against a purchase.
     */
    @PostMapping("/{purchaseUuid}/payments")
    public ResponseEntity<PurchasePaymentSummaryResponse> recordPayment(
            @PathVariable UUID purchaseUuid,
            @Valid @RequestBody PurchasePaymentRequest request
    ) {

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                paymentService.recordPayment(
                    purchaseUuid,
                    request
                )
            );
    }

    /**
     * Get payment history and outstanding balance for a purchase.
     */
    @GetMapping("/{purchaseUuid}/payments")
    public ResponseEntity<PurchasePaymentSummaryResponse> findPaymentsByPurchase(
            @PathVariable UUID purchaseUuid
    ) {

        return ResponseEntity.ok(
            paymentService.findPaymentsByPurchase(purchaseUuid)
        );
    }

    /**
     * Get an individual payment by its UUID.
     */
    @GetMapping("/payments/{paymentUuid}")
    public ResponseEntity<PurchasePaymentResponse> findPaymentByUuid(
            @PathVariable UUID paymentUuid
    ) {

        return ResponseEntity.ok(
            paymentService.findPaymentByUuid(paymentUuid)
        );
    }
}