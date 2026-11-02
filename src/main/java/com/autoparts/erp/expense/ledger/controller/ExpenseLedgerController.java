package com.autoparts.erp.expense.ledger.controller;

import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerResponse;
import com.autoparts.erp.expense.ledger.service.ExpenseLedgerService;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses/ledger")
public class ExpenseLedgerController {

    private final ExpenseLedgerService ledgerService;

    public ExpenseLedgerController(
        ExpenseLedgerService ledgerService
    ) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public ResponseEntity<ExpenseLedgerResponse> getLedger(

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @RequestParam(required = false)
        UUID categoryUuid,

        @RequestParam(required = false)
        ExpensePaymentMethod paymentMethod,

        @RequestParam(required = false)
        String search,

        @RequestParam(defaultValue = "0")
        int page,

        @RequestParam(defaultValue = "20")
        int size
    ) {

        ExpenseLedgerResponse response = ledgerService.getLedger(
            fromDate,
            toDate,
            categoryUuid,
            paymentMethod,
            search,
            page,
            size
        );

        return ResponseEntity.ok(response);
    }
}