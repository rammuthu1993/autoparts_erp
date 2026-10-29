
package com.autoparts.erp.purchase.controller;

import com.autoparts.erp.purchase.dto.PurchaseCreateRequest;
import com.autoparts.erp.purchase.dto.PurchaseResponse;
import com.autoparts.erp.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> create(
        @Valid @RequestBody PurchaseCreateRequest request
    ) {
        PurchaseResponse response = purchaseService.create(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> findAll() {
        return ResponseEntity.ok(purchaseService.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<PurchaseResponse> findByUuid(
        @PathVariable UUID uuid
    ) {
        return ResponseEntity.ok(
            purchaseService.findByUuid(uuid)
        );
    }
    

	@PostMapping("/{uuid}/receive")
	public ResponseEntity<PurchaseResponse> receive(
	    @PathVariable UUID uuid
	) {
	    return ResponseEntity.ok(
	        purchaseService.receive(uuid)
	    );
	}

}
