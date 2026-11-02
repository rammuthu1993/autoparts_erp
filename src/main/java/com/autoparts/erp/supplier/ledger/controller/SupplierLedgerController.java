package com.autoparts.erp.supplier.ledger.controller;

import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerResponse;
import com.autoparts.erp.supplier.ledger.service.SupplierLedgerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierLedgerController {

    private final SupplierLedgerService supplierLedgerService;

    public SupplierLedgerController(
            SupplierLedgerService supplierLedgerService
    ) {
        this.supplierLedgerService = supplierLedgerService;
    }

    @GetMapping("/{supplierUuid}/ledger")
    public ResponseEntity<SupplierLedgerResponse> getSupplierLedger(
            @PathVariable UUID supplierUuid,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                supplierLedgerService.getSupplierLedger(
                        supplierUuid,
                        fromDate,
                        toDate,
                        page,
                        size
                )
        );
    }
}