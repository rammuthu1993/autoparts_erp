package com.autoparts.erp.masters.brand.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.brand.dto.BrandUsageResponse;
import com.autoparts.erp.masters.brand.service.ProductBrandService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/product-brands", "/api/v1/brands"})
public class ProductBrandController {

    private final ProductBrandService service;

    public ProductBrandController(ProductBrandService service) {
        this.service = service;
    }

    @AdminOrManager
    @RequiresPermission("BRANDS.CREATE")
    @PostMapping
    public ResponseEntity<BrandResponse> create(
            @Valid @RequestBody BrandCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/product-brands/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("BRANDS.VIEW")
    @GetMapping
    public ResponseEntity<List<BrandResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @RequiresPermission("BRANDS.VIEW")
    @GetMapping("/active")
    public ResponseEntity<List<BrandResponse>> findAllActive() {
        return ResponseEntity.ok(service.findAllActive());
    }

    @RequiresPermission("BRANDS.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<BrandResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
                service.findPaged(page, size, search, status, sortBy, sortDir)
        );
    }

    @RequiresPermission("BRANDS.VIEW")
    @GetMapping("/{uuid}")
    public ResponseEntity<BrandResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(service.findByUuid(uuid));
    }

    @RequiresPermission("BRANDS.VIEW")
    @GetMapping("/{uuid}/usage")
    public ResponseEntity<BrandUsageResponse> getUsage(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(service.getUsage(uuid));
    }

    @AdminOrManager
    @RequiresPermission("BRANDS.UPDATE")
    @PutMapping("/{uuid}")
    public ResponseEntity<BrandResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody BrandUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @AdminOrManager
    @RequiresPermission("BRANDS.DEACTIVATE")
    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID uuid
    ) {
        service.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }

    @AdminOrManager
    @RequiresPermission("BRANDS.DEACTIVATE")
    @PatchMapping("/{uuid}/reactivate")
    public ResponseEntity<BrandResponse> reactivate(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(service.reactivate(uuid));
    }
}
