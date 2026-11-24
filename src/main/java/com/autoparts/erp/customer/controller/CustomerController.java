package com.autoparts.erp.customer.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.customer.dto.CustomerCreateRequest;
import com.autoparts.erp.customer.dto.CustomerResponse;
import com.autoparts.erp.customer.dto.CustomerUpdateRequest;
import com.autoparts.erp.customer.service.CustomerService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @SalesAccess
    @RequiresPermission("CUSTOMERS.CREATE")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CustomerCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(customerService.create(request));
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/customers/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("CUSTOMERS.VIEW")
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> findAll() {

        return ResponseEntity.ok(customerService.findAll());
    }

    @RequiresPermission("CUSTOMERS.VIEW")
    @GetMapping("/paged")
    public ResponseEntity<PageResponse<CustomerResponse>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "customerName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return ResponseEntity.ok(
                customerService.findPaged(page, size, search, city, state, sortBy, sortDir)
        );
    }

    @RequiresPermission("CUSTOMERS.VIEW")
    @GetMapping("/{uuid}")
    public ResponseEntity<CustomerResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
                customerService.findByUuid(uuid)
        );
    }

    @SalesAccess
    @RequiresPermission("CUSTOMERS.UPDATE")
    @PutMapping("/{uuid}")
    public ResponseEntity<CustomerResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody CustomerUpdateRequest request
    ) {
        return ResponseEntity.ok(
                customerService.update(uuid, request)
        );
    }

    @AdminOrManager
    @RequiresPermission("CUSTOMERS.DEACTIVATE")
    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID uuid
    ) {
        customerService.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }
}