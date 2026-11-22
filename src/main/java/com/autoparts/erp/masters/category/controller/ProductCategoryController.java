package com.autoparts.erp.masters.category.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryResponse;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;
import com.autoparts.erp.masters.category.service.ProductCategoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService service;

    public ProductCategoryController(ProductCategoryService service) {
        this.service = service;
    }

    @AdminOrManager
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/product-categories/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<CategoryResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
                service.findPaged(page, size, search, sortBy, sortDir)
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CategoryResponse> findByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.findByUuid(uuid));
    }

    @AdminOrManager
    @PutMapping("/{uuid}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @AdminOrManager
    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID uuid
    ) {
        service.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }
}