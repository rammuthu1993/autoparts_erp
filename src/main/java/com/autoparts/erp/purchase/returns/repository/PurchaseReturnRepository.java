package com.autoparts.erp.purchase.returns.repository;

import com.autoparts.erp.purchase.returns.model.PurchaseReturn;
import com.autoparts.erp.purchase.returns.model.PurchaseReturnProduct;
import com.autoparts.erp.purchase.returns.model.SupplierCredit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseReturnRepository {

    Optional<PurchaseInfo> findPurchaseByUuidForUpdate(UUID uuid);

    List<OriginalPurchaseProduct> findPurchaseProducts(Long purchaseId);

    BigDecimal getPreviouslyReturnedQuantity(Long purchaseProductId);

    BigDecimal getPreviouslyReturnedAmount(
        Long purchaseProductId,
        String amountColumn
    );

    PurchaseReturn createReturn(PurchaseReturn purchaseReturn);

    void createReturnProduct(PurchaseReturnProduct product);

    Optional<PurchaseReturn> findReturnByUuid(UUID uuid);

    List<PurchaseReturnProduct> findReturnProducts(Long returnId);

    SupplierCredit createSupplierCredit(
        UUID uuid,
        Long purchaseReturnId,
        BigDecimal amount
    );

    Optional<SupplierCredit> findCreditByUuidForUpdate(UUID uuid);

    Optional<SupplierCredit> findCreditByReturnId(Long returnId);

    BigDecimal getRefundedAmount(Long creditId);

    BigDecimal getAppliedAmount(Long creditId);

    void createSupplierRefund(
        UUID uuid,
        Long creditId,
        String paymentMethod,
        BigDecimal amount,
        String referenceNumber,
        String notes,
        Long userId
    );

    void createCreditApplication(
        UUID uuid,
        Long creditId,
        Long purchaseId,
        BigDecimal amount,
        Long userId
    );

    Optional<PurchaseInfo> findPurchaseByUuidForUpdateForCredit(UUID uuid);

    BigDecimal getPurchasePaidAmount(Long purchaseId);

    BigDecimal getPurchaseCreditAppliedAmount(Long purchaseId);

    BigDecimal getProductQuantityForUpdate(Long productId);

    void decreaseProductQuantity(Long productId, BigDecimal quantity);

    void createPurchaseReturnInventoryMovement(
        UUID uuid,
        Long productId,
        Long returnId,
        BigDecimal quantityChange,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter
    );

    record PurchaseInfo(
        Long id,
        UUID uuid,
        Long supplierId,
        String status,
        String purchaseNumber,
        BigDecimal grandTotal
    ) {}

    record OriginalPurchaseProduct(
        Long id,
        UUID uuid,
        Long productId,
        String productCode,
        String productName,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        BigDecimal discountAmount,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal lineTotal
    ) {}
    
    String generateReturnNumber();
    
    Optional<PurchaseInfo> findPurchaseForCreditByReturnId(Long returnId);

    Optional<UUID> findPurchaseUuidById(Long purchaseId);

    Optional<UUID> findPurchaseProductUuidById(Long purchaseProductId);

    Optional<UUID> findReturnUuidById(Long returnId);
    
    Optional<PurchaseInfo> findPurchaseById(Long purchaseId);
    
    Optional<SupplierCredit> findCreditByUuid(UUID creditUuid);
}