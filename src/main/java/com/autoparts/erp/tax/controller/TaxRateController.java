
package com.autoparts.erp.tax.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
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

    @AdminOrManager
    @RequiresPermission("TAX_RATES.CREATE")
    @PostMapping
    public ResponseEntity<TaxRateResponse> create(
        @Valid @RequestBody TaxRateCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(taxRateService.create(request));
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 100, pagedUrl = "/api/v1/tax-rates/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping
    public ResponseEntity<List<TaxRateResponse>> findAll() {
        return ResponseEntity.ok(
            taxRateService.findAll()
        );
    }

    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<TaxRateResponse>> findPaged(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "effectiveFrom") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
            taxRateService.findPaged(page, size, search, sortBy, sortDir)
        );
    }

    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/{uuid}")
    public ResponseEntity<TaxRateResponse> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
            taxRateService.findByUuid(uuid)
        );
    }

    @AdminOrManager
    @RequiresPermission("TAX_RATES.UPDATE")
    @PutMapping("/{uuid}")
    public ResponseEntity<TaxRateResponse> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody TaxRateUpdateRequest request
    ) {
        return ResponseEntity.ok(
            taxRateService.update(uuid, request)
        );
    }

    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/current")
    public ResponseEntity<List<TaxRateResponse>> findCurrent() {
        return ResponseEntity.ok(
            taxRateService.findCurrent()
        );
    }
}
