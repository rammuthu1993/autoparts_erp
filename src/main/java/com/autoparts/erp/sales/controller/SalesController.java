package com.autoparts.erp.sales.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.service.SalesService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @PostMapping
    public ResponseEntity<SalesResponse> create(
        @Valid @RequestBody SalesCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(salesService.create(request));
    }

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

    @GetMapping
    public ResponseEntity<List<SalesResponse>> findAll() {
        return ResponseEntity.ok(
            salesService.findAll()
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
}