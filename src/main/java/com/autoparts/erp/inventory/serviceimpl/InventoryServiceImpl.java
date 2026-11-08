
package com.autoparts.erp.inventory.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.AuthenticatedUser;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.inventory.dto.InventoryMovementResponse;
import com.autoparts.erp.inventory.dto.InventoryPageResponse;
import com.autoparts.erp.inventory.dto.InventoryStockResponse;
import com.autoparts.erp.inventory.dto.StockAdjustmentRequest;
import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
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
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository repository;
    private final AuditLogService auditLogService;

    public InventoryServiceImpl(
            InventoryRepository repository,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------
    // INCREASE STOCK - PURCHASE RECEIPT
    // ADMIN / MANAGER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOrManager
    public InventoryStock increaseStock(
            Long productId,
            BigDecimal quantity,
            Long purchaseId
    ) {

        validateStockQuantity(quantity);

        InventoryStock stock = getStockForUpdate(productId);

        return applyStockChange(
                stock,
                quantity,
                InventoryMovementType.PURCHASE_RECEIPT,
                InventoryReferenceType.PURCHASE,
                purchaseId,
                "Stock received from purchase"
        );
    }

    // ---------------------------------------------------------
    // DECREASE STOCK - SALES
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @SalesAccess
    public InventoryStock decreaseStock(
            Long productId,
            BigDecimal quantity,
            Long saleId
    ) {

        validateStockQuantity(quantity);

        InventoryStock stock = getStockForUpdate(productId);

        return applyStockChange(
                stock,
                quantity.negate(),
                InventoryMovementType.SALE,
                InventoryReferenceType.SALE,
                saleId,
                "Stock reduced through sales invoice"
        );
    }

    // ---------------------------------------------------------
    // INITIALIZE OPENING STOCK
    // ADMIN / MANAGER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOrManager
    public InventoryStock initializeOpeningStock(
            Long productId,
            BigDecimal quantity
    ) {

        validateStockQuantity(quantity);

        InventoryStock stock = getStockForUpdate(productId);

        if (stock.quantity().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException(
                    "Opening stock can only be initialized for an empty product"
            );
        }

        return applyStockChange(
                stock,
                quantity,
                InventoryMovementType.OPENING_STOCK,
                InventoryReferenceType.OPENING_STOCK,
                productId,
                "Initial product stock"
        );
    }

    // ---------------------------------------------------------
    // GET CURRENT STOCK
    // ALL AUTHENTICATED USERS
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
    public InventoryStock getCurrentStock(Long productId) {

        return repository.findStockById(productId)
                .orElseThrow(() ->
                        new ApiException(
                                InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND
                        )
                );
    }

    // ---------------------------------------------------------
    // FIND STOCK BY PRODUCT UUID
    // ALL AUTHENTICATED USERS
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
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

    // ---------------------------------------------------------
    // FIND ALL STOCK
    // ALL AUTHENTICATED USERS
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
    public List<InventoryStockResponse> findAllStock() {

        return repository.findAllStock()
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    // ---------------------------------------------------------
    // FIND LOW STOCK
    // ALL AUTHENTICATED USERS
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
    public List<InventoryStockResponse> findLowStock() {

        return repository.findLowStock()
                .stream()
                .map(this::toStockResponse)
                .toList();
    }

    // ---------------------------------------------------------
    // FIND INVENTORY MOVEMENTS
    // ALL AUTHENTICATED USERS
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
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

    // ---------------------------------------------------------
    // MANUAL STOCK ADJUSTMENT
    // ADMIN / MANAGER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOrManager
    public InventoryStockResponse adjustStock(
            UUID productUuid,
            StockAdjustmentRequest request
    ) {

        BigDecimal quantityChange = request.quantityChange();

        if (quantityChange == null) {
            throw new ApiException(
                    InventoryErrorCode.INVALID_STOCK_QUANTITY
            );
        }

        if (quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(
                    InventoryErrorCode.INVALID_STOCK_ADJUSTMENT
            );
        }

        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(
                    InventoryErrorCode.INVALID_STOCK_ADJUSTMENT
            );
        }

        String reason = request.reason().trim();

        String notes = request.notes() == null
                || request.notes().isBlank()
                ? null
                : request.notes().trim();

        InventoryStock stock = repository
                .findStockByUuidForUpdate(productUuid)
                .orElseThrow(() ->
                        new ApiException(
                                InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND
                        )
                );

        validateStockQuantity(quantityChange.abs());

        UUID adjustmentUuid = UUID.randomUUID();

        Long adjustmentId = repository.createAdjustment(
                adjustmentUuid,
                reason,
                notes
        );

        InventoryStock updatedStock = applyStockChange(
                stock,
                quantityChange,
                InventoryMovementType.ADJUSTMENT,
                InventoryReferenceType.ADJUSTMENT,
                adjustmentId,
                reason
        );

        // Audit manual inventory adjustment.
        auditLogService.log(
                "ADJUST",
                "INVENTORY",
                updatedStock.productCode(),
                "Manual inventory stock adjustment",
                Map.of(
                        "productUuid", productUuid.toString(),
                        "productCode", stock.productCode(),
                        "quantity", stock.quantity()
                ),
                Map.of(
                        "adjustmentUuid", adjustmentUuid.toString(),
                        "reason", reason,
                        "quantityChange", quantityChange,
                        "quantityBefore", stock.quantity(),
                        "quantityAfter", updatedStock.quantity()
                )
        );

        log.info(
                "Inventory stock adjusted. Product UUID: {}, Adjustment UUID: {}, Change: {}, Before: {}, After: {}",
                productUuid,
                adjustmentUuid,
                quantityChange,
                stock.quantity(),
                updatedStock.quantity()
        );

        return toStockResponse(updatedStock);
    }

    // ---------------------------------------------------------
    // GET STOCK FOR UPDATE
    // ---------------------------------------------------------

    private InventoryStock getStockForUpdate(Long productId) {

        return repository.findStockByIdForUpdate(productId)
                .orElseThrow(() ->
                        new ApiException(
                                InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND
                        )
                );
    }

    // ---------------------------------------------------------
    // APPLY STOCK CHANGE
    // ---------------------------------------------------------

    private InventoryStock applyStockChange(
            InventoryStock stock,
            BigDecimal quantityChange,
            InventoryMovementType movementType,
            InventoryReferenceType referenceType,
            Long referenceId,
            String notes
    ) {

        BigDecimal quantityBefore = stock.quantity();

        BigDecimal quantityAfter = quantityBefore.add(quantityChange);

        if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {

            log.warn(
                    "Insufficient stock for product ID {}. Available: {}, change: {}",
                    stock.productId(),
                    quantityBefore,
                    quantityChange
            );

            throw new ApiException(
                    InventoryErrorCode.INSUFFICIENT_STOCK
            );
        }

        repository.updateProductQuantity(
                stock.productId(),
                quantityAfter
        );

        repository.createMovement(
                UUID.randomUUID(),
                stock.productId(),
                movementType,
                referenceType,
                referenceId,
                quantityChange,
                quantityBefore,
                quantityAfter,
                notes
        );

        log.info(
                "Stock changed. Product ID: {}, movement: {}, change: {}, before: {}, after: {}",
                stock.productId(),
                movementType,
                quantityChange,
                quantityBefore,
                quantityAfter
        );

        return new InventoryStock(
                stock.productId(),
                stock.productUuid(),
                stock.productCode(),
                stock.partNo(),
                stock.productName(),
                quantityAfter,
                stock.minimumStock(),
                stock.unit()
        );
    }

    // ---------------------------------------------------------
    // VALIDATE STOCK QUANTITY
    // ---------------------------------------------------------

    private void validateStockQuantity(BigDecimal quantity) {

        if (quantity == null
                || quantity.compareTo(BigDecimal.ZERO) <= 0) {

            throw new ApiException(
                    InventoryErrorCode.INVALID_STOCK_QUANTITY
            );
        }
    }

    // ---------------------------------------------------------
    // BUILD STOCK RESPONSE
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // BUILD MOVEMENT RESPONSE
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // VALIDATE PAGINATION
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // RESTORE STOCK FROM SALES RETURN
    // ADMIN / MANAGER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @SalesAccess
    public InventoryStock restoreStockFromSalesReturn(
            Long productId,
            BigDecimal quantity,
            Long salesReturnId
    ) {
        validateStockQuantity(quantity);

        InventoryStock stock = getStockForUpdate(productId);

        return applyStockChange(
                stock,
                quantity,
                InventoryMovementType.SALES_RETURN,
                InventoryReferenceType.SALES_RETURN,
                salesReturnId,
                "Stock restored from sales return"
        );
    }
}
