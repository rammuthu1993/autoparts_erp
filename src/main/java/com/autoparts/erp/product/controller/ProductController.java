package com.autoparts.erp.product.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.product.dto.ProductCreateRequest;
import com.autoparts.erp.product.dto.ProductResponse;
import com.autoparts.erp.product.dto.ProductUpdateRequest;
import com.autoparts.erp.product.service.ProductBarcodeService;
import com.autoparts.erp.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final ProductBarcodeService barcodeService;

    public ProductController(
            ProductService productService,
            ProductBarcodeService barcodeService
    ) {
        this.productService = productService;
        this.barcodeService = barcodeService;
    }

    @AdminOrManager
    @RequiresPermission("PRODUCTS.CREATE")
    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/products/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<ProductResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(productService.findPaged(page, size, search, categoryId, brandId, sortBy, sortDir));
    }

    /**
     * Fast lookup endpoint intended for POS autocomplete and barcode/part-number
     * searches. It reuses the same repository search semantics as the paged
     * product register instead of introducing a second search implementation.
     */
    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping("/search")
    public ResponseEntity<PageResponse<ProductResponse>> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(productService.findPaged(
                page, size, query, null, null, "name", "asc"
        ));
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping("/{uuid}")
    public ResponseEntity<ProductResponse> findByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(productService.findByUuid(uuid));
    }

    @AdminOrManager
    @RequiresPermission("PRODUCTS.UPDATE")
    @PutMapping("/{uuid}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.update(uuid, request));
    }

    @AdminOrManager
    @RequiresPermission("PRODUCTS.DEACTIVATE")
    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID uuid) {
        productService.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping("/{uuid}/barcode")
    public ResponseEntity<ProductBarcodeResponse> getBarcode(@PathVariable UUID uuid) {
        ProductResponse product = barcodeService.getBarcodeDetails(uuid);
        return ResponseEntity.ok(new ProductBarcodeResponse(
                product.uuid(),
                product.productCode(),
                product.name(),
                product.barcode(),
                product.barcodeType()
        ));
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping(value = "/{uuid}/barcode/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewBarcode(@PathVariable UUID uuid) {
        byte[] image = barcodeService.generatePng(uuid);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(image.length)
                .body(image);
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping(value = "/{uuid}/barcode/download", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> downloadBarcode(@PathVariable UUID uuid) {
        ProductResponse product = barcodeService.getBarcodeDetails(uuid);
        byte[] image = barcodeService.generatePng(uuid);
        String filename = product.productCode() + "-barcode.png";

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentLength(image.length)
                .body(image);
    }

    public record ProductBarcodeResponse(
            UUID productUuid,
            String productCode,
            String productName,
            String barcode,
            String barcodeType
    ) {}
}
