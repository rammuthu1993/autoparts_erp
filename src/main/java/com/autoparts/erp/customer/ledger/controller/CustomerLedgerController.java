package com.autoparts.erp.customer.ledger.controller;

import com.autoparts.erp.customer.ledger.dto.CustomerLedgerResponse;
import com.autoparts.erp.customer.ledger.service.CustomerLedgerService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerLedgerController {

    private final CustomerLedgerService customerLedgerService;

    public CustomerLedgerController(
            CustomerLedgerService customerLedgerService
    ) {
        this.customerLedgerService = customerLedgerService;
    }

    @GetMapping("/{customerUuid}/ledger")
    public ResponseEntity<CustomerLedgerResponse> getCustomerLedger(
            @PathVariable UUID customerUuid,

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
                customerLedgerService.getCustomerLedger(
                        customerUuid,
                        fromDate,
                        toDate,
                        page,
                        size
                )
        );
    }
}