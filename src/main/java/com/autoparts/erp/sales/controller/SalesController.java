package com.autoparts.erp.sales.controller;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.sales.dto.SalesCancelRequest;
import com.autoparts.erp.sales.dto.SalesCancellationPreviewResponse;
import com.autoparts.erp.sales.dto.SalesCancellationRefundConfirmRequest;
import com.autoparts.erp.sales.dto.SalesCancellationResponse;
import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesPaymentRequest;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.dto.SalesStatusOption;
import com.autoparts.erp.sales.dto.SalesSummaryResponse;
import com.autoparts.erp.sales.repository.SalesRepository;
import com.autoparts.erp.sales.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService salesService;
    private final SalesRepository salesRepository;

    public SalesController(SalesService salesService, SalesRepository salesRepository) {
        this.salesService = salesService;
        this.salesRepository = salesRepository;
    }

    @RequiresPermission("SALES.VIEW")
    @GetMapping("/status-options")
    public ResponseEntity<List<SalesStatusOption>> statusOptions() {
        return ResponseEntity.ok(salesRepository.findSalesStatuses());
    }

    @RequiresPermission("SALES.VIEW")
    @GetMapping("/payment-status-options")
    public ResponseEntity<List<SalesStatusOption>> paymentStatusOptions() {
        return ResponseEntity.ok(salesRepository.findSalesPaymentStatuses());
    }

    @SalesAccess
    @RequiresPermission("SALES.CREATE")
    @PostMapping
    public ResponseEntity<SalesResponse> create(
        @Valid @RequestBody SalesCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(salesService.create(request));
    }

    @SalesAccess
    @RequiresPermission("SALES.CREATE")
    @PutMapping("/{saleUuid}")
    public ResponseEntity<SalesResponse> updateDraft(
        @PathVariable UUID saleUuid,
        @Valid @RequestBody SalesCreateRequest request
    ) {
        return ResponseEntity.ok(salesService.updateDraft(saleUuid, request));
    }

    @SalesAccess
    @RequiresPermission("SALES.COMPLETE")
    @PostMapping("/{saleUuid}/complete")
    public ResponseEntity<SalesResponse> complete(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(salesService.complete(saleUuid));
    }

    @RequiresPermission("SALES.VIEW")
    @GetMapping("/summary")
    public ResponseEntity<SalesSummaryResponse> summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ResponseEntity.ok(salesService.getSummary(fromDate, toDate));
    }

    @RequiresPermission("SALES.VIEW")
    @GetMapping("/{saleUuid}")
    public ResponseEntity<SalesResponse> findByUuid(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(salesService.findByUuid(saleUuid));
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/sales/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("SALES.VIEW")
    @GetMapping
    public ResponseEntity<List<SalesResponse>> findAll() {
        return ResponseEntity.ok(salesService.findAll());
    }

    @RequiresPermission("SALES.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<SalesResponse>> findPaged(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required = false) UUID customerUuid,
        @RequestParam(required = false) String paymentMethod,
        @RequestParam(required = false) String returnStatus,
        @RequestParam(required = false) BigDecimal minAmount,
        @RequestParam(required = false) BigDecimal maxAmount,
        @RequestParam(defaultValue = "id") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
            salesService.findPaged(
                page, size, search, status, paymentStatus,
                fromDate, toDate, customerUuid, paymentMethod, returnStatus,
                minAmount, maxAmount, sortBy, sortDir
            )
        );
    }

    @RequiresPermission("SALES.CANCEL_ISSUED")
    @GetMapping("/{saleUuid}/cancellation-preview")
    public ResponseEntity<SalesCancellationPreviewResponse> cancellationPreview(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(salesService.getCancellationPreview(saleUuid));
    }

    @RequiresPermission("SALES.VIEW")
    @GetMapping("/{saleUuid}/cancellation")
    public ResponseEntity<SalesCancellationResponse> getCancellation(
        @PathVariable UUID saleUuid
    ) {
        return ResponseEntity.ok(salesService.getCancellation(saleUuid));
    }

    @RequiresPermission("SALES.CANCEL")
    @PostMapping("/{saleUuid}/cancel")
    public ResponseEntity<SalesResponse> cancel(
        @PathVariable UUID saleUuid,
        @Valid @RequestBody SalesCancelRequest request
    ) {
        return ResponseEntity.ok(salesService.cancel(saleUuid, request));
    }

    @RequiresPermission("SALES.CANCEL_ISSUED")
    @PostMapping("/{saleUuid}/cancellation/refunds/{refundUuid}/confirm")
    public ResponseEntity<SalesCancellationResponse> confirmCancellationRefund(
        @PathVariable UUID saleUuid,
        @PathVariable UUID refundUuid,
        @Valid @RequestBody SalesCancellationRefundConfirmRequest request
    ) {
        return ResponseEntity.ok(
            salesService.confirmCancellationRefund(saleUuid, refundUuid, request)
        );
    }

    @RequiresPermission("SALES.PAYMENT_RECORD")
    @PostMapping("/{saleUuid}/payments")
    public ResponseEntity<SalesResponse> recordPayment(
        @PathVariable UUID saleUuid,
        @Valid @RequestBody SalesPaymentRequest request
    ) {
        return ResponseEntity.ok(salesService.recordPayment(saleUuid, request));
    }
}
