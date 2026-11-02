package com.autoparts.erp.inventory.service;

import com.autoparts.erp.inventory.dto.InventoryMovementResponse;
import com.autoparts.erp.inventory.dto.InventoryPageResponse;
import com.autoparts.erp.inventory.dto.InventoryStockResponse;
import com.autoparts.erp.inventory.dto.StockAdjustmentRequest;
import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.model.InventoryStock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryStock increaseStock(
            Long productId,
            BigDecimal quantity,
            Long purchaseId
    );

    InventoryStock decreaseStock(
            Long productId,
            BigDecimal quantity,
            Long saleId
    );

    InventoryStock initializeOpeningStock(
            Long productId,
            BigDecimal quantity
    );

    InventoryStock getCurrentStock(Long productId);

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