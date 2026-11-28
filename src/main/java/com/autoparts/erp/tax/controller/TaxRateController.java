package com.autoparts.erp.tax.controller;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.tax.dto.TaxRateCloseRequest;
import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateResponse;
import com.autoparts.erp.tax.dto.TaxRateSupersedeRequest;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.dto.TaxSummaryResponse;
import com.autoparts.erp.tax.service.TaxRateService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax-rates")
public class TaxRateController {

    private final TaxRateService taxRateService;

    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    @AdminOnly
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
    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping
    public ResponseEntity<List<TaxRateResponse>> findAll() {
        return ResponseEntity.ok(taxRateService.findAll());
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<TaxRateResponse>> findPaged(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) BigDecimal gstRate,
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) String periodStatus,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveOn,
        @RequestParam(defaultValue = "effectiveFrom") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
            taxRateService.findPaged(page, size, search, gstRate, active, periodStatus, effectiveOn, sortBy, sortDir)
        );
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/current")
    public ResponseEntity<List<TaxRateResponse>> findCurrent() {
        return ResponseEntity.ok(taxRateService.findCurrent());
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/selectable")
    public ResponseEntity<List<TaxRateResponse>> findSelectable(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate
    ) {
        return ResponseEntity.ok(taxRateService.findSelectable(effectiveDate));
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/summary")
    public ResponseEntity<TaxSummaryResponse> getSummary() {
        return ResponseEntity.ok(taxRateService.getSummary());
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/{uuid}")
    public ResponseEntity<TaxRateResponse> findByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(taxRateService.findByUuid(uuid));
    }

    @AdminOnly
    @RequiresPermission("TAX_RATES.UPDATE")
    @PutMapping("/{uuid}")
    public ResponseEntity<TaxRateResponse> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody TaxRateUpdateRequest request
    ) {
        return ResponseEntity.ok(taxRateService.update(uuid, request));
    }

    @AdminOnly
    @RequiresPermission("TAX_RATES.UPDATE")
    @PostMapping("/{uuid}/close")
    public ResponseEntity<TaxRateResponse> closePeriod(
        @PathVariable UUID uuid,
        @Valid @RequestBody TaxRateCloseRequest request
    ) {
        return ResponseEntity.ok(taxRateService.closePeriod(uuid, request));
    }

    @AdminOnly
    @RequiresPermission("TAX_RATES.UPDATE")
    @PostMapping("/{uuid}/supersede")
    public ResponseEntity<TaxRateResponse> supersede(
        @PathVariable UUID uuid,
        @Valid @RequestBody TaxRateSupersedeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxRateService.supersede(uuid, request));
    }
}
