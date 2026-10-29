
package com.autoparts.erp.purchase.service;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.purchase.dto.PurchaseCreateRequest;
import com.autoparts.erp.purchase.dto.PurchaseProductRequest;
import com.autoparts.erp.purchase.dto.PurchaseProductResponse;
import com.autoparts.erp.purchase.dto.PurchaseResponse;
import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseErrorCode;
import com.autoparts.erp.purchase.model.PurchaseProduct;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import com.autoparts.erp.purchase.repository.PurchaseRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseRepository repository;

    public PurchaseServiceImpl(PurchaseRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PurchaseResponse create(PurchaseCreateRequest request) {

        validateUniqueProducts(request.products());

        if (!repository.supplierExistsAndActive(request.supplierId())) {
            throw new ApiException(
                PurchaseErrorCode.SUPPLIER_NOT_FOUND
            );
        }

        String invoiceNo = request.supplierInvoiceNo().trim();

        if (repository.existsBySupplierInvoiceNo(
            request.supplierId(), invoiceNo
        )) {
            throw new ApiException(
                PurchaseErrorCode.PURCHASE_ALREADY_EXISTS
            );
        }

        for (PurchaseProductRequest item : request.products()) {
            if (!repository.productExistsAndActive(item.productId())) {
                throw new ApiException(
                    PurchaseErrorCode.PRODUCT_NOT_FOUND
                );
            }
        }

        UUID purchaseUuid = UUID.randomUUID();
        String purchaseNumber = repository.nextPurchaseNumber();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        // Calculate all totals before inserting the header.
        for (PurchaseProductRequest item : request.products()) {
            BigDecimal gross = item.quantity()
                .multiply(item.purchasePrice())
                .setScale(2, RoundingMode.HALF_UP);

            BigDecimal discount = money(item.discountAmount());

            if (discount.compareTo(gross) > 0) {
                throw new ApiException(
                    PurchaseErrorCode.INVALID_PURCHASE_DATA
                );
            }

            BigDecimal taxable = gross.subtract(discount);

            BigDecimal tax = taxable
                .multiply(item.taxPercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(gross);
            discountTotal = discountTotal.add(discount);
            taxTotal = taxTotal.add(tax);
            grandTotal = grandTotal.add(taxable).add(tax);
        }

        try {
            repository.createPurchase(
                purchaseUuid,
                purchaseNumber,
                request.supplierId(),
                invoiceNo,
                request.purchaseDate(),
                clean(request.notes())
            );

            for (PurchaseProductRequest item : request.products()) {
                BigDecimal gross = item.quantity()
                    .multiply(item.purchasePrice())
                    .setScale(2, RoundingMode.HALF_UP);

                BigDecimal discount = money(item.discountAmount());
                BigDecimal taxable = gross.subtract(discount);

                BigDecimal tax = taxable
                    .multiply(item.taxPercent())
                    .divide(
                        new BigDecimal("100"),
                        2,
                        RoundingMode.HALF_UP
                    );

                BigDecimal lineTotal = taxable.add(tax);

                repository.createPurchaseItem(
                    UUID.randomUUID(),
                    purchaseUuid,
                    item.productId(),
                    item.quantity(),
                    money(item.purchasePrice()),
                    discount,
                    item.taxPercent(),
                    tax,
                    lineTotal
                );
            }

            repository.updatePurchaseTotals(
                purchaseUuid,
                subtotal,
                discountTotal,
                taxTotal,
                grandTotal
            );

        } catch (DuplicateKeyException exception) {
            if (isConstraint(exception, "uq_purchase_supplier_invoice_normalized")) {
                throw new ApiException(
                    PurchaseErrorCode.PURCHASE_ALREADY_EXISTS
                );
            }

            if (isConstraint(exception, "uq_purchase_item_product")) {
                throw new ApiException(
                    PurchaseErrorCode.PURCHASE_PRODUCT_ALREADY_EXISTS
                );
            }

            throw exception;
        }

        return findByUuid(purchaseUuid);
    }

    @Override
    public List<PurchaseResponse> findAll() {
        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public PurchaseResponse findByUuid(UUID uuid) {
        Purchase purchase = repository.findByUuid(uuid)
            .orElseThrow(() ->
                new ApiException(PurchaseErrorCode.PURCHASE_NOT_FOUND)
            );

        return toResponse(purchase);
    }

    private void validateUniqueProducts(
        List<PurchaseProductRequest> products
    ) {
        Set<Long> productIds = new HashSet<>();

        for (PurchaseProductRequest product : products) {
            if (!productIds.add(product.productId())) {
                throw new ApiException(
                    PurchaseErrorCode.PURCHASE_PRODUCT_ALREADY_EXISTS
                );
            }
        }
    }

    private PurchaseResponse toResponse(Purchase purchase) {
        List<PurchaseProductResponse> products = purchase.products()
            .stream()
            .map(this::toItemResponse)
            .toList();

        return new PurchaseResponse(
            purchase.id(),
            purchase.uuid(),
            purchase.purchaseNumber(),
            purchase.supplierId(),
            purchase.supplierName(),
            purchase.supplierInvoiceNo(),
            purchase.purchaseDate(),
            purchase.status(),
            purchase.subtotal(),
            purchase.discountTotal(),
            purchase.taxTotal(),
            purchase.grandTotal(),
            purchase.notes(),
            products,
            purchase.createdAt(),
            purchase.updatedAt(),
            purchase.receivedAt()
        );
    }

    private PurchaseProductResponse toItemResponse(PurchaseProduct product) {
        return new PurchaseProductResponse(
            product.uuid(),
            product.productId(),
//            product.itemId(),
            product.partNo(),
            product.productName(),
            product.quantity(),
            product.purchasePrice(),
            product.discountAmount(),
            product.taxPercent(),
            product.taxAmount(),
            product.lineTotal()
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        return value == null || value.isBlank()
            ? null
            : value.trim();
    }

    private boolean isConstraint(
        DuplicateKeyException exception,
        String constraintName
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause.getMessage() != null
                && cause.getMessage().contains(constraintName)) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
    
    @Override
	@Transactional
	public PurchaseResponse receive(UUID uuid) {
	
	    Purchase purchase = repository.findByUuidForUpdate(uuid)
	        .orElseThrow(() ->
	            new ApiException(PurchaseErrorCode.PURCHASE_NOT_FOUND)
	        );
	
	    if (purchase.status() == PurchaseStatus.RECEIVED) {
	        throw new ApiException(
	            PurchaseErrorCode.PURCHASE_ALREADY_RECEIVED
	        );
	    }
	
	    if (purchase.status() != PurchaseStatus.DRAFT) {
	        throw new ApiException(
	            PurchaseErrorCode.PURCHASE_NOT_DRAFT
	        );
	    }
	
	    if (purchase.products() == null || purchase.products().isEmpty()) {
	        throw new ApiException(
	            PurchaseErrorCode.PURCHASE_PRODUCTS_NOT_FOUND
	        );
	    }
	
	    // Lock products in a consistent order to reduce deadlock risk.
	    List<PurchaseProduct> products = purchase.products().stream()
	        .sorted(Comparator.comparing(
	            PurchaseProduct::productId
	        ))
	        .toList();
	
	    for (PurchaseProduct product : products) {
	
	        BigDecimal quantityBefore;
	
	        try {
	            quantityBefore = repository.findProductQtyForUpdate(
	                product.productId()
	            );
	        } catch (
	            org.springframework.dao.EmptyResultDataAccessException ex
	        ) {
	            throw new ApiException(
	                PurchaseErrorCode.PRODUCT_NOT_FOUND
	            );
	        }
	
	        BigDecimal quantityAfter = quantityBefore.add(
	            product.quantity()
	        );
	
	        repository.increaseProductQty(
	            product.productId(),
	            product.quantity()
	        );
	
	        repository.createInventoryMovement(
	            UUID.randomUUID(),
	            product.productId(),
	            purchase.id(),
	            product.quantity(),
	            quantityBefore,
	            quantityAfter
	        );
	    }
	
	    if (!repository.markReceived(uuid)) {
	        throw new ApiException(
	            PurchaseErrorCode.PURCHASE_NOT_DRAFT
	        );
	    }
	
	    return findByUuid(uuid);
	}

}
