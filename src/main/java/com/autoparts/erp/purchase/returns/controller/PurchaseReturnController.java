package com.autoparts.erp.purchase.returns.controller;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.purchase.returns.dto.*;
import com.autoparts.erp.purchase.returns.service.PurchaseReturnService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

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
    @RequiresPermission("PURCHASE_RETURNS.CREATE")
    public PurchaseReturnResponse createReturn(
            @PathVariable UUID purchaseUuid,
            @Valid @RequestBody PurchaseReturnRequest request
    ) {
        return service.createReturn(purchaseUuid, request);
    }

    // List all return documents for an original purchase.
    @GetMapping("/purchases/{purchaseUuid}/returns")
    @RequiresPermission("PURCHASE_RETURNS.VIEW")
    public List<PurchaseReturnResponse> findReturnsByPurchase(
            @PathVariable UUID purchaseUuid
    ) {
        return service.findReturnsByPurchaseUuid(purchaseUuid);
    }

    // Get paginated purchase returns register.
    @GetMapping("/purchase-returns/paged")
    @RequiresPermission("PURCHASE_RETURNS.VIEW")
    public com.autoparts.erp.common.dto.PageResponse<PurchaseReturnResponse> findReturnsPaged(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String creditStatus,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.findReturnsPaged(search, creditStatus, fromDate, toDate, page, size);
    }

    // Get purchase return details.
    @GetMapping("/purchase-returns/{returnUuid}")
    @RequiresPermission("PURCHASE_RETURNS.VIEW")
    public PurchaseReturnResponse findReturn(
            @PathVariable UUID returnUuid
    ) {
        return service.findReturnByUuid(returnUuid);
    }

    // Record actual supplier refund.
    @PostMapping("/supplier-credits/{creditUuid}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("PURCHASE_RETURNS.CREATE")
    public SupplierCreditResponse recordRefund(
            @PathVariable UUID creditUuid,
            @Valid @RequestBody SupplierRefundRequest request
    ) {
        return service.recordRefund(creditUuid, request);
    }

    // Apply supplier credit to another purchase.
    @PostMapping("/supplier-credits/{creditUuid}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("PURCHASE_RETURNS.CREATE")
    public SupplierCreditResponse applyCredit(
            @PathVariable UUID creditUuid,
            @Valid @RequestBody SupplierCreditApplicationRequest request
    ) {
        return service.applyCredit(creditUuid, request);
    }
}
