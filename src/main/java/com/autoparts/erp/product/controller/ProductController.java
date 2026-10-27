
package com.autoparts.erp.product.controller;

import com.autoparts.erp.product.dto.ProductCreateRequest;
import com.autoparts.erp.product.dto.ProductResponse;
import com.autoparts.erp.product.dto.ProductUpdateRequest;
import com.autoparts.erp.product.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Create product
    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Get all products
    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    // Get product by UUID
    @GetMapping("/{uuid}")
    public ResponseEntity<ProductResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
                productService.findByUuid(uuid)
        );
    }

    // Update product
    @PutMapping("/{uuid}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(
                productService.update(uuid, request)
        );
    }

    // Deactivate product
    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID uuid
    ) {
        productService.deactivate(uuid);

        return ResponseEntity.noContent().build();
    }
}
