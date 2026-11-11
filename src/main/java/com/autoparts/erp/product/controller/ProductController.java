package com.autoparts.erp.product.controller;

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

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ProductResponse> findByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(productService.findByUuid(uuid));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.update(uuid, request));
    }

    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID uuid) {
        productService.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }

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

    @GetMapping(value = "/{uuid}/barcode/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewBarcode(@PathVariable UUID uuid) {
        byte[] image = barcodeService.generatePng(uuid);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(image.length)
                .body(image);
    }

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
