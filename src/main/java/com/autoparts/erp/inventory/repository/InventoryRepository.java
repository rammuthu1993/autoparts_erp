package com.autoparts.erp.inventory.repository;

import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
import com.autoparts.erp.inventory.model.InventoryMovement;
import com.autoparts.erp.inventory.model.InventoryStock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {

    Optional<InventoryStock> findStockByUuid(UUID productUuid);

    Optional<InventoryStock> findStockById(Long productId);

    Optional<InventoryStock> findStockByUuidForUpdate(UUID productUuid);

    Optional<InventoryStock> findStockByIdForUpdate(Long productId);

    List<InventoryStock> findAllStock();

    List<InventoryStock> findLowStock();

    Long createAdjustment(
            UUID uuid,
            String reason,
            String notes
    );

    void updateProductQuantity(
            Long productId,
            BigDecimal quantity
    );

    void createMovement(
            UUID uuid,
            Long productId,
            InventoryMovementType movementType,
            InventoryReferenceType referenceType,
            Long referenceId,
            BigDecimal quantityChange,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String notes
    );

    List<InventoryMovement> findMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate,
            int limit,
            int offset
    );

    long countMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate
    );
}