
package com.autoparts.erp.masters.supplier.controller;

import com.autoparts.erp.masters.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.masters.supplier.dto.SupplierResponse;
import com.autoparts.erp.masters.supplier.dto.SupplierUpdateRequest;
import com.autoparts.erp.masters.supplier.service.SupplierService;
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

    @GetMapping("/{uuid}")
    public ResponseEntity<SupplierResponse> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(supplierService.findByUuid(uuid));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<SupplierResponse> update(
        @PathVariable UUID uuid,
        @Valid @RequestBody SupplierUpdateRequest request
    ) {
        return ResponseEntity.ok(supplierService.update(uuid, request));
    }

    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
        @PathVariable UUID uuid
    ) {
        supplierService.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }
}
