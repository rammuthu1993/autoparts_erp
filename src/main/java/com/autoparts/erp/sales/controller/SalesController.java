package com.autoparts.erp.sales.controller;

import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.service.SalesService;
import com.autoparts.erp.sales.dto.SalesPaymentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @SalesAccess
    @PostMapping
    public ResponseEntity<SalesResponse> create(
        @Valid @RequestBody SalesCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(salesService.create(request));
    }

    @SalesAccess
    @PostMapping("/{saleUuid}/complete")
    public ResponseEntity<SalesResponse> complete(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(
            salesService.complete(saleUuid)
        );
    }

    @GetMapping("/{saleUuid}")
    public ResponseEntity<SalesResponse> findByUuid(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(
            salesService.findByUuid(saleUuid)
        );
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/sales/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @GetMapping
    public ResponseEntity<List<SalesResponse>> findAll() {
        return ResponseEntity.ok(
            salesService.findAll()
        );
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<SalesResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
                salesService.findPaged(page, size, search, status, paymentStatus, sortBy, sortDir)
        );
    }

    @PostMapping("/{saleUuid}/cancel")
    public ResponseEntity<SalesResponse> cancel(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(
            salesService.cancel(saleUuid)
        );
    }
    
    @PostMapping("/{saleUuid}/payments")
    public ResponseEntity<SalesResponse> recordPayment(
        @PathVariable UUID saleUuid,
        @Valid @RequestBody SalesPaymentRequest request
    ) {
        return ResponseEntity.ok(
            salesService.recordPayment(saleUuid, request)
        );
    }
}