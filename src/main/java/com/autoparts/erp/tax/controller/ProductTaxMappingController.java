package com.autoparts.erp.tax.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingCloseRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingSupersedeRequest;
import com.autoparts.erp.tax.service.ProductTaxMappingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productUuid}/tax-mappings")
public class ProductTaxMappingController {

    private final ProductTaxMappingService mappingService;

    public ProductTaxMappingController(ProductTaxMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    @PostMapping
    public ResponseEntity<ProductTaxMappingResponse> create(
        @PathVariable UUID productUuid,
        @Valid @RequestBody ProductTaxMappingCreateRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mappingService.create(productUuid, request));
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping
    public ResponseEntity<List<ProductTaxMappingResponse>> findByProduct(
        @PathVariable UUID productUuid
    ) {
        return ResponseEntity.ok(mappingService.findByProductUuid(productUuid));
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<ProductTaxMappingResponse>> findPagedByProduct(
        @PathVariable UUID productUuid,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ResponseEntity.ok(
            mappingService.findPagedByProductUuid(productUuid, page, size, fromDate, toDate)
        );
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/current")
    public ResponseEntity<ProductTaxMappingResponse> findCurrent(
        @PathVariable UUID productUuid
    ) {
        return ResponseEntity.ok(mappingService.findCurrentByProductUuid(productUuid));
    }

    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    @PostMapping("/{mappingUuid}/close")
    public ResponseEntity<ProductTaxMappingResponse> closePeriod(
        @PathVariable UUID productUuid,
        @PathVariable UUID mappingUuid,
        @Valid @RequestBody ProductTaxMappingCloseRequest request
    ) {
        return ResponseEntity.ok(mappingService.closePeriod(productUuid, mappingUuid, request));
    }

    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    @PostMapping("/{mappingUuid}/supersede")
    public ResponseEntity<ProductTaxMappingResponse> supersede(
        @PathVariable UUID productUuid,
        @PathVariable UUID mappingUuid,
        @Valid @RequestBody ProductTaxMappingSupersedeRequest request
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mappingService.supersede(productUuid, mappingUuid, request));
    }
}
