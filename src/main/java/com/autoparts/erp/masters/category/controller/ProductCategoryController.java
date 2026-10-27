package com.autoparts.erp.masters.category.controller;

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

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CategoryResponse> findByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.findByUuid(uuid));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody CategoryUpdateRequest request
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