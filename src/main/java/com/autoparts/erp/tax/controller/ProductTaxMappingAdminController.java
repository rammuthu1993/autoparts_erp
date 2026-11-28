package com.autoparts.erp.tax.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignRequest;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;
import com.autoparts.erp.tax.service.ProductTaxMappingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product-tax-mappings")
public class ProductTaxMappingAdminController {

    private final ProductTaxMappingService mappingService;

    public ProductTaxMappingAdminController(ProductTaxMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @SalesAccess
    @RequiresPermission("TAX_RATES.VIEW")
    @GetMapping("/{mappingUuid}")
    public ResponseEntity<ProductTaxMappingResponse> findByUuid(@PathVariable UUID mappingUuid) {
        return ResponseEntity.ok(mappingService.findByUuid(mappingUuid));
    }

    @AdminOrManager
    @RequiresPermission("PRODUCT_TAX_MAPPINGS.MANAGE")
    @PostMapping("/bulk-assign")
    public ResponseEntity<BulkProductTaxAssignResponse> bulkAssign(
        @Valid @RequestBody BulkProductTaxAssignRequest request
    ) {
        return ResponseEntity.ok(mappingService.bulkAssign(request));
    }
}
