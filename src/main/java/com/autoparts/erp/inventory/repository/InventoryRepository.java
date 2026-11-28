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

    List<InventoryStock> findStockPaged(
        int offset,
        int limit,
        String search,
        String sortBy,
        String sortDir
    );

    long countStockPaged(String search);

    List<InventoryStock> findLowStock();

    List<InventoryStock> findLowStockPaged(
        int offset,
        int limit,
        String search,
        String sortBy,
        String sortDir
    );

    long countLowStockPaged(String search);

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
            String search,
            int limit,
            int offset
    );

    long countMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            LocalDate fromDate,
            LocalDate toDate,
            String search
    );

    com.autoparts.erp.inventory.dto.InventorySummaryResponse getInventorySummary();

    List<InventoryStock> findStockPaged(
        int offset,
        int limit,
        String search,
        String stockStatus,
        String sortBy,
        String sortDir
    );

    long countStockPaged(String search, String stockStatus);



    Optional<InventoryMovement> findMovementByUuid(UUID movementUuid);

    void addQuarantineOrScrapQuantity(Long productId, BigDecimal quantity, boolean scrap);
}