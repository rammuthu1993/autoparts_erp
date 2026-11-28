package com.autoparts.erp.sales.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.sales.dto.SalesRefundRequest;
import com.autoparts.erp.sales.dto.SalesReturnRequest;
import com.autoparts.erp.sales.dto.SalesReturnResponse;
import com.autoparts.erp.sales.service.SalesReturnService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesReturnController {

    private final SalesReturnService salesReturnService;

    public SalesReturnController(
            SalesReturnService salesReturnService
    ) {
        this.salesReturnService = salesReturnService;
    }

    @RequiresPermission("SALES_RETURNS.CREATE")
    @PostMapping("/{saleUuid}/returns")
    public ResponseEntity<SalesReturnResponse> createReturn(
            @PathVariable UUID saleUuid,
            @Valid @RequestBody SalesReturnRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        salesReturnService.createReturn(
                                saleUuid,
                                request
                        )
                );
    }

    @RequiresPermission("SALES_RETURNS.VIEW")
    @GetMapping("/{saleUuid}/returns")
    public ResponseEntity<List<SalesReturnResponse>> findReturnsBySale(
            @PathVariable UUID saleUuid
    ) {

        return ResponseEntity.ok(
                salesReturnService.findReturnsBySaleUuid(saleUuid)
        );
    }

    @RequiresPermission("SALES_RETURNS.VIEW")
    @GetMapping("/returns/paged")
    public ResponseEntity<PageResponse<SalesReturnResponse>> findReturnsPaged(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                salesReturnService.findReturnsPaged(search, status, fromDate, toDate, page, size)
        );
    }

    @RequiresPermission("SALES_RETURNS.VIEW")
    @GetMapping("/returns/{returnUuid}")
    public ResponseEntity<SalesReturnResponse> findReturn(
            @PathVariable UUID returnUuid
    ) {

        return ResponseEntity.ok(
                salesReturnService.findReturnByUuid(returnUuid)
        );
    }

    @RequiresPermission("SALES_RETURNS.REFUND")
    @PostMapping("/returns/{returnUuid}/refund")
    public ResponseEntity<SalesReturnResponse> recordRefund(
            @PathVariable UUID returnUuid,
            @Valid @RequestBody SalesRefundRequest request
    ) {

        return ResponseEntity.ok(
                salesReturnService.recordRefund(
                        returnUuid,
                        request
                )
        );
    }
}