
package com.autoparts.erp.tax.controller;

import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;
import com.autoparts.erp.tax.service.ProductTaxMappingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productUuid}/tax-mappings")
public class ProductTaxMappingController {

    private final ProductTaxMappingService mappingService;

    public ProductTaxMappingController(
        ProductTaxMappingService mappingService
    ) {
        this.mappingService = mappingService;
    }

    @PostMapping
    public ResponseEntity<ProductTaxMappingResponse> create(
        @PathVariable UUID productUuid,
        @Valid @RequestBody ProductTaxMappingCreateRequest request
    ) {

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mappingService.create(productUuid, request));
    }

    @GetMapping
    public ResponseEntity<List<ProductTaxMappingResponse>> findByProduct(
        @PathVariable UUID productUuid
    ) {

        return ResponseEntity.ok(
            mappingService.findByProductUuid(productUuid)
        );
    }

    @GetMapping("/current")
    public ResponseEntity<ProductTaxMappingResponse> findCurrent(
        @PathVariable UUID productUuid
    ) {

        return ResponseEntity.ok(
            mappingService.findCurrentByProductUuid(productUuid)
        );
    }
}
