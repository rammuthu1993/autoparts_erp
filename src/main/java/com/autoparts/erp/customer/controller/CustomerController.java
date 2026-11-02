package com.autoparts.erp.customer.controller;

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

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CustomerCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(customerService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> findAll() {

        return ResponseEntity.ok(customerService.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CustomerResponse> findByUuid(
            @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
                customerService.findByUuid(uuid)
        );
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<CustomerResponse> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody CustomerUpdateRequest request
    ) {
        return ResponseEntity.ok(
                customerService.update(uuid, request)
        );
    }

    @PatchMapping("/{uuid}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID uuid
    ) {
        customerService.deactivate(uuid);
        return ResponseEntity.noContent().build();
    }
}