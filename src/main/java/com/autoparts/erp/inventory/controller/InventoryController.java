package com.autoparts.erp.inventory.controller;

import com.autoparts.erp.inventory.dto.InventoryMovementResponse;
import com.autoparts.erp.inventory.dto.InventoryPageResponse;
import com.autoparts.erp.inventory.dto.InventoryStockResponse;
import com.autoparts.erp.inventory.dto.StockAdjustmentRequest;
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
    @GetMapping("/products/{productUuid}/stock")
    public InventoryStockResponse findStockByProductUuid(
            @PathVariable UUID productUuid
    ) {

        return inventoryService.findStockByProductUuid(productUuid);
    }

    /**
     * Get stock for all active Products.
     */
    @GetMapping("/stock")
    public List<InventoryStockResponse> findAllStock() {

        return inventoryService.findAllStock();
    }

    /**
     * Get Products whose current quantity is less than
     * or equal to their minimum stock.
     */
    @GetMapping("/low-stock")
    public List<InventoryStockResponse> findLowStock() {

        return inventoryService.findLowStock();
    }

    /**
     * Get inventory movement history.
     *
     * All filters are optional.
     */
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
                page,
                size
        );
    }

    /**
     * Manually increase or decrease Product stock.
     */
    @PostMapping("/products/{productUuid}/adjustments")
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