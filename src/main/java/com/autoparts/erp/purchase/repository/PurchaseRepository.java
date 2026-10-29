
package com.autoparts.erp.purchase.repository;

import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseProduct;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository {

    boolean supplierExistsAndActive(Long supplierId);

    boolean productExistsAndActive(Long productId);

    boolean existsBySupplierInvoiceNo(
        Long supplierId,
        String invoiceNo
    );

    String nextPurchaseNumber();

    Purchase createPurchase(
        UUID uuid,
        String purchaseNumber,
        Long supplierId,
        String supplierInvoiceNo,
        java.time.LocalDate purchaseDate,
        String notes
    );

    PurchaseProduct createPurchaseItem(
        UUID uuid,
        UUID purchaseUuid,
        Long productId,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        BigDecimal discountAmount,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal lineTotal
    );

    void updatePurchaseTotals(
        UUID purchaseUuid,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal
    );

    Optional<Purchase> findByUuid(UUID uuid);

    List<Purchase> findAll();
    

	Optional<Purchase> findByUuidForUpdate(UUID uuid);
	
	BigDecimal findProductQtyForUpdate(Long productId);
	
	void increaseProductQty(
	    Long productId,
	    BigDecimal quantity
	);
	
	void createInventoryMovement(
	    UUID uuid,
	    Long productId,
	    Long purchaseId,
	    BigDecimal quantityChange,
	    BigDecimal quantityBefore,
	    BigDecimal quantityAfter
	);
	
	boolean markReceived(UUID purchaseUuid);

}
