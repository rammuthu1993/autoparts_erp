package com.autoparts.erp.expense.category.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryCreateRequest;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryResponse;
import com.autoparts.erp.expense.category.dto.ExpenseCategoryUpdateRequest;
import com.autoparts.erp.expense.category.service.ExpenseCategoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expense-categories")
public class ExpenseCategoryController {

    private final ExpenseCategoryService service;

    public ExpenseCategoryController(
            ExpenseCategoryService service
    ) {
        this.service = service;
    }

    @AdminOrManager
    @PostMapping
    public ResponseEntity<ExpenseCategoryResponse> create(
            @Valid @RequestBody ExpenseCategoryCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(service.create(request));
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/expense-categories/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @GetMapping
    public ResponseEntity<List<ExpenseCategoryResponse>> findAll() {

        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<ExpenseCategoryResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return ResponseEntity.ok(
                service.findPaged(page, size, search, sortBy, sortDir)
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ExpenseCategoryResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(service.findByUuid(uuid));
    }

    @AdminOrManager
    @PutMapping("/{uuid}")
    public ResponseEntity<ExpenseCategoryResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ExpenseCategoryUpdateRequest request
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