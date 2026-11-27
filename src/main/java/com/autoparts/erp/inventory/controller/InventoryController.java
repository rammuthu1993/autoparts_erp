package com.autoparts.erp.inventory.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.inventory.dto.InventoryMovementResponse;
import com.autoparts.erp.inventory.dto.InventoryPageResponse;
import com.autoparts.erp.inventory.dto.InventoryStockResponse;
import com.autoparts.erp.inventory.dto.InventorySummaryResponse;
import com.autoparts.erp.inventory.dto.StockAdjustmentRequest;
import com.autoparts.erp.inventory.dto.StockReconciliationRequest;
import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.service.InventoryService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Get stock for one Product.
     */
    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/products/{productUuid}/stock")
    public InventoryStockResponse findStockByProductUuid(
            @PathVariable UUID productUuid
    ) {

        return inventoryService.findStockByProductUuid(productUuid);
    }

    /**
     * Get stock for all active Products.
     */
    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/inventory/stock/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/stock")
    public List<InventoryStockResponse> findAllStock() {

        return inventoryService.findAllStock();
    }

    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/summary")
    public InventorySummaryResponse getSummary() {
        return inventoryService.getInventorySummary();
    }

    /**
     * Get stock for all active Products with pagination.
     */
    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/stock/paged")
    public PageResponse<InventoryStockResponse> findStockPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "productName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return inventoryService.findStockPaged(page, size, search, stockStatus, sortBy, sortDir);
    }

    /**
     * Get Products whose current quantity is less than
     * or equal to their minimum stock.
     */
    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/inventory/low-stock/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/low-stock")
    public List<InventoryStockResponse> findLowStock() {

        return inventoryService.findLowStock();
    }

    /**
     * Get Products whose current quantity is less than
     * or equal to their minimum stock with pagination.
     */
    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/low-stock/paged")
    public PageResponse<InventoryStockResponse> findLowStockPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "quantity") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return inventoryService.findLowStockPaged(page, size, search, sortBy, sortDir);
    }

    /**
     * Get inventory movement history.
     *
     * All filters are optional.
     */
    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/movements")
    public InventoryPageResponse<InventoryMovementResponse> findMovements(

            @RequestParam(required = false)
            UUID productUuid,

            @RequestParam(required = false)
            InventoryMovementType movementType,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false)
            String search,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return inventoryService.findMovements(
                productUuid,
                movementType,
                fromDate,
                toDate,
                search,
                page,
                size
        );
    }

    @RequiresPermission("INVENTORY.VIEW")
    @GetMapping("/movements/{movementUuid}")
    public InventoryMovementResponse findMovementByUuid(@PathVariable UUID movementUuid) {
        return inventoryService.findMovementByUuid(movementUuid);
    }

    @PostMapping("/products/{productUuid}/reconcile")
    @AdminOrManager
    @RequiresPermission("INVENTORY.RECONCILE")
    public InventoryStockResponse reconcileStock(
            @PathVariable UUID productUuid,
            @Valid @RequestBody StockReconciliationRequest request
    ) {
        return inventoryService.reconcileStock(productUuid, request);
    }

    /**
     * Manually increase or decrease Product stock.
     */
    @PostMapping("/products/{productUuid}/adjustments")
    @AdminOrManager
    @RequiresPermission("INVENTORY.ADJUST")
    public InventoryStockResponse adjustStock(

            @PathVariable UUID productUuid,

            @Valid
            @RequestBody StockAdjustmentRequest request
    ) {

        return inventoryService.adjustStock(
                productUuid,
                request
        );
    }
}