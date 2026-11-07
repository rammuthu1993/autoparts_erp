package com.autoparts.erp.purchase.returns.controller;

import com.autoparts.erp.purchase.returns.dto.*;
import com.autoparts.erp.purchase.returns.service.PurchaseReturnService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PurchaseReturnController {

    private final PurchaseReturnService service;

    public PurchaseReturnController(PurchaseReturnService service) {
        this.service = service;
    }

    // Create purchase return and generate supplier credit.
    @PostMapping("/purchases/{purchaseUuid}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseReturnResponse createReturn(
            @PathVariable UUID purchaseUuid,
            @Valid @RequestBody PurchaseReturnRequest request
    ) {
        return service.createReturn(purchaseUuid, request);
    }

    // Get purchase return details.
    @GetMapping("/purchase-returns/{returnUuid}")
    public PurchaseReturnResponse findReturn(
            @PathVariable UUID returnUuid
    ) {
        return service.findReturnByUuid(returnUuid);
    }

    // Record actual supplier refund.
    @PostMapping("/supplier-credits/{creditUuid}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierCreditResponse recordRefund(
            @PathVariable UUID creditUuid,
            @Valid @RequestBody SupplierRefundRequest request
    ) {
        return service.recordRefund(creditUuid, request);
    }

    // Apply supplier credit to another purchase.
    @PostMapping("/supplier-credits/{creditUuid}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierCreditResponse applyCredit(
            @PathVariable UUID creditUuid,
            @Valid @RequestBody SupplierCreditApplicationRequest request
    ) {
        return service.applyCredit(creditUuid, request);
    }
}