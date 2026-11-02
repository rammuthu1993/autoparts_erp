package com.autoparts.erp.salesledger.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.salesledger.dto.SalesLedgerPageResponse;
import com.autoparts.erp.salesledger.service.SalesLedgerService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@Validated
@RequestMapping("/api/v1/sales-ledger")
public class SalesLedgerController {

    private final SalesLedgerService salesLedgerService;

    public SalesLedgerController(
        SalesLedgerService salesLedgerService
    ) {
        this.salesLedgerService = salesLedgerService;
    }

    @GetMapping
    public ResponseEntity<SalesLedgerPageResponse> getSalesLedger(

        @RequestParam
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @RequestParam
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @RequestParam(required = false)
        String search,

        @RequestParam(defaultValue = "0")
        @Min(0)
        int page,

        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(100)
        int size
    ) {

        return ResponseEntity.ok(
            salesLedgerService.getSalesLedger(
                fromDate,
                toDate,
                search,
                page,
                size
            )
        );
    }
}