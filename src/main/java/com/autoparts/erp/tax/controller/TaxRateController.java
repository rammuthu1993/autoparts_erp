
package com.autoparts.erp.tax.controller;

import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.service.TaxRateService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax-rates")
public class TaxRateController {

    private final TaxRateService taxRateService;

    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    @PostMapping
    public ResponseEntity<TaxRateResponse> create(
        @Valid @RequestBody TaxRateCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(taxRateService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TaxRateResponse>> findAll() {
        return ResponseEntity.ok(
            taxRateService.findAll()
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<TaxRateResponse> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
            taxRateService.findByUuid(uuid)
        );
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<TaxRateResponse> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody TaxRateUpdateRequest request
    ) {
        return ResponseEntity.ok(
            taxRateService.update(uuid, request)
        );
    }

    @GetMapping("/current")
    public ResponseEntity<List<TaxRateResponse>> findCurrent() {
        return ResponseEntity.ok(
            taxRateService.findCurrent()
        );
    }
}
