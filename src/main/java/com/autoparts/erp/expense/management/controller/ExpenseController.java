package com.autoparts.erp.expense.management.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.expense.management.dto.CreateExpenseRequest;
import com.autoparts.erp.expense.management.dto.UpdateExpenseRequest;
import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.service.ExpenseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @AdminOrManager
    @PostMapping
    public ResponseEntity<Expense> create(
        @Valid @RequestBody CreateExpenseRequest request
    ) {
        Expense expense = expenseService.create(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(expense);
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/expenses/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @GetMapping
    public ResponseEntity<List<Expense>> findAll() {

        return ResponseEntity.ok(
            expenseService.findAll()
        );
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<Expense>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
                expenseService.findPaged(page, size, search, status, paymentMethod, sortBy, sortDir)
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Expense> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
            expenseService.findByUuid(uuid)
        );
    }

    @AdminOrManager
    @PutMapping("/{uuid}")
    public ResponseEntity<Expense> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody UpdateExpenseRequest request
    ) {
        return ResponseEntity.ok(
            expenseService.update(uuid, request)
        );
    }

    @AdminOrManager
    @PatchMapping("/{uuid}/cancel")
    public ResponseEntity<Void> cancel(
        @PathVariable UUID uuid
    ) {
        expenseService.cancel(uuid);

        return ResponseEntity.noContent().build();
    }
}