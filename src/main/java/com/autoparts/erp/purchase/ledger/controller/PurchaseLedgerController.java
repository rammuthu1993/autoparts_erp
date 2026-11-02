package com.autoparts.erp.purchase.ledger.controller;

import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerResponse;
import com.autoparts.erp.purchase.ledger.service.PurchaseLedgerService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases/ledger")
public class PurchaseLedgerController {

    private final PurchaseLedgerService ledgerService;

    public PurchaseLedgerController(
        PurchaseLedgerService ledgerService
    ) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public ResponseEntity<PurchaseLedgerResponse> getLedger(

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @RequestParam(required = false)
        UUID supplierUuid,

        @RequestParam(required = false)
        String search,

        @RequestParam(defaultValue = "0")
        int page,

        @RequestParam(defaultValue = "20")
        int size
    ) {

        PurchaseLedgerResponse response = ledgerService.getLedger(
            fromDate,
            toDate,
            supplierUuid,
            search,
            page,
            size
        );

        return ResponseEntity.ok(response);
    }
}