package com.autoparts.erp.expense.management.controller;

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

    @PostMapping
    public ResponseEntity<Expense> create(
        @Valid @RequestBody CreateExpenseRequest request
    ) {
        Expense expense = expenseService.create(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(expense);
    }

    @GetMapping
    public ResponseEntity<List<Expense>> findAll() {

        return ResponseEntity.ok(
            expenseService.findAll()
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

    @PutMapping("/{uuid}")
    public ResponseEntity<Expense> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody UpdateExpenseRequest request
    ) {
        return ResponseEntity.ok(
            expenseService.update(uuid, request)
        );
    }

    @PatchMapping("/{uuid}/cancel")
    public ResponseEntity<Void> cancel(
        @PathVariable UUID uuid
    ) {
        expenseService.cancel(uuid);

        return ResponseEntity.noContent().build();
    }
}