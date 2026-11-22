
package com.autoparts.erp.supplier.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.supplier.dto.SupplierResponse;
import com.autoparts.erp.supplier.dto.SupplierUpdateRequest;
import com.autoparts.erp.supplier.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @AdminOrManager
    @PostMapping
    public ResponseEntity<SupplierResponse> create(
        @Valid @RequestBody SupplierCreateRequest request
    ) {
        SupplierResponse response = supplierService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SupplierResponse>> findAll() {
        return ResponseEntity.ok(supplierService.findAll());
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<SupplierResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "supplierName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return ResponseEntity.ok(
                supplierService.findPaged(page, size, search, city, state, sortBy, sortDir)
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<SupplierResponse> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(supplierService.findByUuid(uuid));
    }

    @AdminOrManager
    @PutMapping("/{uuid}")
    public ResponseEntity<SupplierResponse> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody SupplierUpdateRequest request
    ) {
        return ResponseEntity.ok(supplierService.update(uuid, request));
    }

    @AdminOrManager
    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
        @PathVariable UUID uuid
    ) {
        supplierService.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }
}
