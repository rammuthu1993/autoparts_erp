package com.autoparts.erp.expense.category.controller;

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

    @PostMapping
    public ResponseEntity<ExpenseCategoryResponse> create(
            @Valid @RequestBody ExpenseCategoryCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<ExpenseCategoryResponse>> findAll() {

        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ExpenseCategoryResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(service.findByUuid(uuid));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<ExpenseCategoryResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ExpenseCategoryUpdateRequest request
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