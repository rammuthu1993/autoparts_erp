package com.autoparts.erp.inventory.serviceimpl;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.inventory.dto.InventoryMovementResponse;
import com.autoparts.erp.inventory.dto.InventoryPageResponse;
import com.autoparts.erp.inventory.dto.InventoryStockResponse;
import com.autoparts.erp.inventory.dto.StockAdjustmentRequest;
import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.exception.InventoryErrorCode;
import com.autoparts.erp.inventory.model.InventoryMovement;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repository.InventoryRepository;
import com.autoparts.erp.inventory.service.InventoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository repository;

    public InventoryServiceImpl(InventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public InventoryStockResponse findStockByProductUuid(
            UUID productUuid
    ) {

        InventoryStock stock = repository.findStockByUuid(productUuid)
                .orElseThrow(() ->
                        new ApiException(
                                InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND
                        )
                );

        return toStockResponse(stock);
    }

    @Override
    public List<InventoryStockResponse> findAllStock() {

        return repository.findAllStock()
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    @Override
    public List<InventoryStockResponse> findLowStock() {

        return repository.findLowStock()
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    @Override
    public InventoryPageResponse<InventoryMovementResponse> findMovements(
            UUID productUuid,
            InventoryMovementType movementType,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate,
            int page,
            int size
    ) {

        validatePagination(page, size);

        if (fromDate != null && toDate != null
                && fromDate.isAfter(toDate)) {

            throw new ApiException(
                    InventoryErrorCode.INVALID_INVENTORY_DATE_RANGE
            );
        }

        int offset = Math.multiplyExact(page, size);

        List<InventoryMovementResponse> movements =
                repository.findMovements(
                                productUuid,
                                movementType,
                                fromDate,
                                toDate,
                                size,
                                offset
                        )
                        .stream()
                        .map(this::toMovementResponse)
                        .toList();

        long totalElements = repository.countMovements(
                productUuid,
                movementType,
                fromDate,
                toDate
        );

        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);

        return new InventoryPageResponse<>(
                movements,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1
        );
    }

    @Override
    @Transactional
    public InventoryStockResponse adjustStock(
            UUID productUuid,
            StockAdjustmentRequest request
    ) {

        BigDecimal quantityChange = request.quantityChange();

        if (quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(
                    InventoryErrorCode.INVALID_STOCK_ADJUSTMENT
            );
        }

        String reason = request.reason().trim();

        String notes = request.notes() == null
                || request.notes().isBlank()
                ? null
                : request.notes().trim();

        // Lock the Product row before calculating the new quantity.
        InventoryStock stock = repository
                .findStockByUuidForUpdate(productUuid)
                .orElseThrow(() ->
                        new ApiException(
                                InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND
                        )
                );

        BigDecimal quantityBefore = stock.quantity();

        BigDecimal quantityAfter = quantityBefore.add(quantityChange);

        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(
                    InventoryErrorCode.INSUFFICIENT_STOCK
            );
        }

        UUID adjustmentUuid = UUID.randomUUID();

        // Create an adjustment header to provide a valid reference ID.
        Long adjustmentId = repository.createAdjustment(
                adjustmentUuid,
                reason,
                notes
        );

        // Update current stock.
        repository.updateProductQuantity(
                stock.productId(),
                quantityAfter
        );

        // Record the stock movement in the existing V6 table.
        repository.createAdjustmentMovement(
                UUID.randomUUID(),
                stock.productId(),
                adjustmentId,
                quantityChange,
                quantityBefore,
                quantityAfter,
                reason
        );
//        repository.createAdjustmentMovement(
//                UUID.randomUUID(),
//                stock.productId(),
//                adjustmentId,
//                quantityChange,
//                quantityBefore,
//                quantityAfter,
//                reason + (notes == null ? "" : " - " + notes)
//        );

        log.info(
                "Inventory stock adjusted. Product UUID: {}, Adjustment UUID: {}, Change: {}, Before: {}, After: {}",
                productUuid,
                adjustmentUuid,
                quantityChange,
                quantityBefore,
                quantityAfter
        );

        return new InventoryStockResponse(
                stock.productUuid(),
                stock.productCode(),
                stock.partNo(),
                stock.productName(),
                quantityAfter,
                stock.minimumStock(),
                stock.unit(),
                quantityAfter.compareTo(stock.minimumStock()) <= 0
        );
    }

    private InventoryStockResponse toStockResponse(
            InventoryStock stock
    ) {

        return new InventoryStockResponse(
                stock.productUuid(),
                stock.productCode(),
                stock.partNo(),
                stock.productName(),
                stock.quantity(),
                stock.minimumStock(),
                stock.unit(),
                stock.quantity().compareTo(stock.minimumStock()) <= 0
        );
    }

    private InventoryMovementResponse toMovementResponse(
            InventoryMovement movement
    ) {

        return new InventoryMovementResponse(
                movement.uuid(),
                movement.productUuid(),
                movement.productCode(),
                movement.productName(),
                movement.movementType(),
                movement.referenceType(),
                movement.referenceId(),
                movement.quantityChange(),
                movement.quantityBefore(),
                movement.quantityAfter(),
                movement.notes(),
                movement.createdAt()
        );
    }

    private void validatePagination(int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(
                    InventoryErrorCode.INVALID_INVENTORY_PAGINATION
            );
        }

        try {
            Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new ApiException(
                    InventoryErrorCode.INVALID_INVENTORY_PAGINATION
            );
        }
    }
}