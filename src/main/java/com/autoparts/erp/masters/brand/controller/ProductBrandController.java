package com.autoparts.erp.masters.brand.controller;

import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.brand.service.ProductBrandService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product-brands")
public class ProductBrandController {

    private final ProductBrandService service;

    public ProductBrandController(ProductBrandService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BrandResponse> create(
            @Valid @RequestBody BrandCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<BrandResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<BrandResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(service.findByUuid(uuid));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<BrandResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody BrandUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID uuid
    ) {
        service.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }
}