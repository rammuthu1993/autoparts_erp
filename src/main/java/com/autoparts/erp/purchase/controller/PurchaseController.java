package com.autoparts.erp.purchase.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.purchase.dto.PurchaseCreateRequest;
import com.autoparts.erp.purchase.dto.PurchaseResponse;
import com.autoparts.erp.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @AdminOrManager
    @RequiresPermission("PURCHASES.CREATE")
    @PostMapping
    public ResponseEntity<PurchaseResponse> create(
        @Valid @RequestBody PurchaseCreateRequest request
    ) {
        PurchaseResponse response = purchaseService.create(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(
            limit = 500,
            pagedUrl = "/api/v1/purchases/paged",
            deprecationDate = "2026-11-22T00:00:00Z"
    )
    @RequiresPermission("PURCHASES.VIEW")
    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> findAll() {

        return ResponseEntity.ok(
            purchaseService.findAll()
        );
    }

    @RequiresPermission("PURCHASES.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<PurchaseResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return ResponseEntity.ok(
                purchaseService.findPaged(page, size, search, status, sortBy, sortDir)
        );
    }

    @RequiresPermission("PURCHASES.VIEW")
    @GetMapping("/{uuid}")
    public ResponseEntity<PurchaseResponse> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
            purchaseService.findByUuid(uuid)
        );
    }

    @AdminOrManager
    @RequiresPermission("PURCHASES.RECEIVE")
    @PostMapping("/{uuid}/receive")
    public ResponseEntity<PurchaseResponse> receive(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
            purchaseService.receive(uuid)
        );
    }
}
