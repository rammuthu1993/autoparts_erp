package com.autoparts.erp.inventory.service;

import com.autoparts.erp.inventory.dto.InventoryMovementResponse;
import com.autoparts.erp.inventory.dto.InventoryPageResponse;
import com.autoparts.erp.inventory.dto.InventoryStockResponse;
import com.autoparts.erp.inventory.dto.StockAdjustmentRequest;
import com.autoparts.erp.inventory.enums.InventoryMovementType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryStockResponse findStockByProductUuid(UUID productUuid);

    List<InventoryStockResponse> findAllStock();

    List<InventoryStockResponse> findLowStock();

    InventoryPageResponse<InventoryMovementResponse> findMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );

    InventoryStockResponse adjustStock(
            UUID productUuid,
            StockAdjustmentRequest request
    );
}