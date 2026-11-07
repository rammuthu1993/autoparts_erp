package com.autoparts.erp.purchase.returns.serviceimpl;

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.purchase.returns.dto.*;
import com.autoparts.erp.purchase.returns.enums.SupplierRefundPaymentMethod;
import com.autoparts.erp.purchase.returns.model.*;
import com.autoparts.erp.purchase.returns.repository.PurchaseReturnRepository;
import com.autoparts.erp.purchase.returns.repository.PurchaseReturnRepository.OriginalPurchaseProduct;
import com.autoparts.erp.purchase.returns.repository.PurchaseReturnRepository.PurchaseInfo;
import com.autoparts.erp.purchase.returns.service.PurchaseReturnService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final PurchaseReturnRepository repository;
    private final CurrentUserService currentUserService;
    private final CashRegisterService cashRegisterService;

    public PurchaseReturnServiceImpl(
            PurchaseReturnRepository repository,
            CurrentUserService currentUserService,
            CashRegisterService cashRegisterService
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.cashRegisterService = cashRegisterService;
    }

    @Override
    @Transactional
    public PurchaseReturnResponse createReturn(
            UUID purchaseUuid,
            PurchaseReturnRequest request
    ) {
        PurchaseInfo purchase = repository
            .findPurchaseByUuidForUpdate(purchaseUuid)
            .orElseThrow(() -> notFound("Purchase not found"));

        if (!"RECEIVED".equals(purchase.status())) {
            throw badRequest(
                "Only received purchases can be returned"
            );
        }

        if (request.products() == null || request.products().isEmpty()) {
            throw badRequest("At least one product is required");
        }

        List<OriginalPurchaseProduct> originalProducts =
            repository.findPurchaseProducts(purchase.id());

        Map<UUID, OriginalPurchaseProduct> productMap = new HashMap<>();

        for (OriginalPurchaseProduct product : originalProducts) {
            productMap.put(product.uuid(), product);
        }

        Set<UUID> processedProducts = new HashSet<>();

        UUID returnUuid = UUID.randomUUID();

        PurchaseReturn newReturn = new PurchaseReturn(
            null,
            returnUuid,
            repository.generateReturnNumber(),
            purchase.id(),
            OffsetDateTime.now(),
            request.reason().trim(),
            clean(request.notes()),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null
        );

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal returnTotal = BigDecimal.ZERO;

        List<PurchaseReturnProduct> returnProducts = new ArrayList<>();

        for (PurchaseReturnProductRequest productRequest : request.products()) {

            if (!processedProducts.add(productRequest.purchaseProductUuid())) {
                throw badRequest(
                    "Duplicate purchase product in return request"
                );
            }

            OriginalPurchaseProduct original = productMap.get(
                productRequest.purchaseProductUuid()
            );

            if (original == null) {
                throw badRequest(
                    "Product does not belong to this purchase"
                );
            }

            BigDecimal quantity = productRequest.quantity();

            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw badRequest("Return quantity must be greater than zero");
            }

            BigDecimal previousQuantity =
                repository.getPreviouslyReturnedQuantity(original.id());

            BigDecimal remainingQuantity =
                original.quantity().subtract(previousQuantity);

            if (quantity.compareTo(remainingQuantity) > 0) {
                throw badRequest(
                    "Return quantity exceeds remaining received quantity for product "
                        + original.productCode()
                        + ". Remaining quantity: "
                        + remainingQuantity
                );
            }

            BigDecimal newReturnedQuantity =
                previousQuantity.add(quantity);

            boolean finalReturn = newReturnedQuantity.compareTo(
                original.quantity()
            ) == 0;

            BigDecimal discount = allocateAmount(
                original.discountAmount(),
                original.quantity(),
                quantity,
                repository.getPreviouslyReturnedAmount(
                    original.id(), "discount_amount"
                ),
                finalReturn
            );

            BigDecimal tax = allocateAmount(
                original.taxAmount(),
                original.quantity(),
                quantity,
                repository.getPreviouslyReturnedAmount(
                    original.id(), "tax_amount"
                ),
                finalReturn
            );

            BigDecimal lineTotal = allocateAmount(
                original.lineTotal(),
                original.quantity(),
                quantity,
                repository.getPreviouslyReturnedAmount(
                    original.id(), "line_total"
                ),
                finalReturn
            );

            BigDecimal lineSubtotal = money(
                original.purchasePrice().multiply(quantity)
            );

            PurchaseReturnProduct returnProduct =
                new PurchaseReturnProduct(
                    null,
                    UUID.randomUUID(),
                    null,
                    original.id(),
                    original.productId(),
                    original.productCode(),
                    original.productName(),
                    quantity,
                    original.purchasePrice(),
                    discount,
                    original.taxPercent(),
                    tax,
                    lineTotal
                );

            returnProducts.add(returnProduct);

            subtotal = subtotal.add(lineSubtotal);
            discountTotal = discountTotal.add(discount);
            taxTotal = taxTotal.add(tax);
            returnTotal = returnTotal.add(lineTotal);
        }

        subtotal = money(subtotal);
        discountTotal = money(discountTotal);
        taxTotal = money(taxTotal);
        returnTotal = money(returnTotal);

        if (returnTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest(
                "Purchase return amount must be greater than zero"
            );
        }

        PurchaseReturn savedReturn = repository.createReturn(
            new PurchaseReturn(
                null,
                returnUuid,
                newReturn.returnNumber(),
                purchase.id(),
                newReturn.returnDate(),
                newReturn.reason(),
                newReturn.notes(),
                subtotal,
                discountTotal,
                taxTotal,
                returnTotal,
                null,
                null
            )
        );

        for (PurchaseReturnProduct product : returnProducts) {

            PurchaseReturnProduct savedProduct =
                new PurchaseReturnProduct(
                    null,
                    product.uuid(),
                    savedReturn.id(),
                    product.purchaseProductId(),
                    product.productId(),
                    product.productCode(),
                    product.productName(),
                    product.quantity(),
                    product.purchasePrice(),
                    product.discountAmount(),
                    product.taxPercent(),
                    product.taxAmount(),
                    product.lineTotal()
                );

            repository.createReturnProduct(savedProduct);

            // Lock the product stock row before reducing stock.
            BigDecimal quantityBefore =
                repository.getProductQuantityForUpdate(product.productId());

            if (quantityBefore.compareTo(product.quantity()) < 0) {
                throw badRequest(
                    "Insufficient stock to return product "
                        + product.productCode()
                );
            }

            BigDecimal quantityAfter =
                quantityBefore.subtract(product.quantity());

            repository.decreaseProductQuantity(
                product.productId(),
                product.quantity()
            );

            repository.createPurchaseReturnInventoryMovement(
                UUID.randomUUID(),
                product.productId(),
                savedReturn.id(),
                product.quantity().negate(),
                quantityBefore,
                quantityAfter
            );
        }

        repository.createSupplierCredit(
            UUID.randomUUID(),
            savedReturn.id(),
            returnTotal
        );

        return findReturnByUuid(savedReturn.uuid());
    }

    @Override
    public PurchaseReturnResponse findReturnByUuid(UUID returnUuid) {

        PurchaseReturn purchaseReturn = repository
            .findReturnByUuid(returnUuid)
            .orElseThrow(() ->
                notFound("Purchase return not found")
            );

        PurchaseInfo purchase = repository
            .findPurchaseById(purchaseReturn.purchaseId())
            .orElseThrow(() ->
                notFound("Purchase not found")
            );

        return buildResponse(purchaseReturn, purchase);
    }

    @Override
    @Transactional
    public SupplierCreditResponse recordRefund(
            UUID creditUuid,
            SupplierRefundRequest request
    ) {
        SupplierCredit credit = repository
            .findCreditByUuidForUpdate(creditUuid)
            .orElseThrow(() -> notFound("Supplier credit not found"));

        BigDecimal refunded = repository.getRefundedAmount(credit.id());
        BigDecimal applied = repository.getAppliedAmount(credit.id());

        BigDecimal available = credit.amount()
            .subtract(refunded)
            .subtract(applied);

        BigDecimal amount = money(request.amount());

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("Refund amount must be greater than zero");
        }

        if (amount.compareTo(available) > 0) {
            throw badRequest(
                "Refund amount exceeds available supplier credit. Available: "
                    + available
            );
        }

        UUID refundUuid = UUID.randomUUID();

        Long userId = currentUserService.getCurrentUserId();

        repository.createSupplierRefund(
            refundUuid,
            credit.id(),
            request.paymentMethod().name(),
            amount,
            clean(request.referenceNumber()),
            clean(request.notes()),
            userId
        );

        /*
         * Only CASH changes the physical cash register balance.
         * UPI, CARD and BANK refunds are recorded in supplier_refunds.
         */
        if (request.paymentMethod() == SupplierRefundPaymentMethod.CASH) {
            cashRegisterService.recordTransactionMovement(
                CashMovementType.IN,
                CashMovementSourceType.SUPPLIER_REFUND,
                refundUuid,
                amount,
                "Cash refund received from supplier"
            );
        }

        return buildCreditResponse(credit.uuid());
    }

    @Override
    @Transactional
    public SupplierCreditResponse applyCredit(
            UUID creditUuid,
            SupplierCreditApplicationRequest request
    ) {
        SupplierCredit credit = repository
            .findCreditByUuidForUpdate(creditUuid)
            .orElseThrow(() -> notFound("Supplier credit not found"));

        PurchaseInfo originalPurchase = repository
            .findPurchaseForCreditByReturnId(credit.purchaseReturnId())
            .orElseThrow(() -> notFound("Original purchase not found"));

        PurchaseInfo targetPurchase = repository
            .findPurchaseByUuidForUpdateForCredit(request.purchaseUuid())
            .orElseThrow(() -> notFound("Target purchase not found"));

        if (!Objects.equals(
                originalPurchase.supplierId(),
                targetPurchase.supplierId()
        )) {
            throw badRequest(
                "Supplier credit can only be applied to the same supplier"
            );
        }

        if (!"RECEIVED".equals(targetPurchase.status())) {
            throw badRequest(
                "Supplier credit can only be applied to a received purchase"
            );
        }

        BigDecimal amount = money(request.amount());

        BigDecimal refunded = repository.getRefundedAmount(credit.id());
        BigDecimal applied = repository.getAppliedAmount(credit.id());

        BigDecimal availableCredit = credit.amount()
            .subtract(refunded)
            .subtract(applied);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest(
                "Credit application amount must be greater than zero"
            );
        }

        if (amount.compareTo(availableCredit) > 0) {
            throw badRequest(
                "Application amount exceeds available supplier credit"
            );
        }

        BigDecimal paidAmount =
            repository.getPurchasePaidAmount(targetPurchase.id());

        BigDecimal alreadyApplied =
            repository.getPurchaseCreditAppliedAmount(targetPurchase.id());

        BigDecimal outstanding = targetPurchase.grandTotal()
            .subtract(paidAmount)
            .subtract(alreadyApplied);

        if (amount.compareTo(outstanding) > 0) {
            throw badRequest(
                "Application amount exceeds purchase outstanding balance. "
                    + "Outstanding: " + outstanding
            );
        }

        repository.createCreditApplication(
            UUID.randomUUID(),
            credit.id(),
            targetPurchase.id(),
            amount,
            currentUserService.getCurrentUserId()
        );

        return buildCreditResponse(credit.uuid());
    }

    private PurchaseReturnResponse buildResponse(
            PurchaseReturn purchaseReturn,
            PurchaseInfo purchase
    ) {
        List<PurchaseReturnProductResponse> products =
            repository.findReturnProducts(purchaseReturn.id())
                .stream()
                .map(product -> new PurchaseReturnProductResponse(
                    product.uuid(),
                    findPurchaseProductUuid(product.purchaseProductId()),
                    product.productId(),
                    product.productCode(),
                    product.productName(),
                    product.quantity(),
                    product.purchasePrice(),
                    product.discountAmount(),
                    product.taxPercent(),
                    product.taxAmount(),
                    product.lineTotal()
                ))
                .toList();

        SupplierCredit credit = repository
            .findCreditByReturnId(purchaseReturn.id())
            .orElseThrow(() -> notFound("Supplier credit not found"));

        SupplierCreditResponse creditResponse =
            buildCreditResponse(credit.uuid());

        return new PurchaseReturnResponse(
            purchaseReturn.uuid(),
            purchaseReturn.returnNumber(),
            purchase.uuid(),
            purchase.purchaseNumber(),
            purchaseReturn.returnDate(),
            purchaseReturn.reason(),
            purchaseReturn.notes(),
            purchaseReturn.subtotal(),
            purchaseReturn.discountTotal(),
            purchaseReturn.taxTotal(),
            purchaseReturn.totalReturnAmount(),
            products,
            creditResponse,
            purchaseReturn.createdAt()
        );
    }

    private SupplierCreditResponse buildCreditResponse(UUID creditUuid) {
        SupplierCredit credit = repository
            .findCreditByUuid(creditUuid)
            .orElseThrow(() -> notFound("Supplier credit not found"));

        BigDecimal refunded = repository.getRefundedAmount(credit.id());
        BigDecimal applied = repository.getAppliedAmount(credit.id());

        return new SupplierCreditResponse(
            credit.uuid(),
            findReturnUuid(credit.purchaseReturnId()),
            credit.creditNoteNumber(),
            credit.creditDate(),
            credit.amount(),
            refunded,
            applied,
            credit.amount().subtract(refunded).subtract(applied)
        );
    }

    private BigDecimal allocateAmount(
            BigDecimal originalAmount,
            BigDecimal originalQuantity,
            BigDecimal requestedQuantity,
            BigDecimal previousReturnedAmount,
            boolean finalReturn
    ) {
        if (finalReturn) {
            return money(originalAmount.subtract(previousReturnedAmount));
        }

        return money(
            originalAmount.multiply(requestedQuantity)
                .divide(originalQuantity, 8, ROUNDING)
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    private String clean(String value) {
        return value == null || value.isBlank()
            ? null
            : value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            message
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        );
    }

    /*
     * These three lookup methods are small repository additions.
     * Their SQL implementations are shown in the next section.
     */
    private UUID findPurchaseUuid(Long purchaseId) {
        return repository.findPurchaseUuidById(purchaseId)
            .orElseThrow(() -> notFound("Purchase not found"));
    }

    private UUID findPurchaseProductUuid(Long purchaseProductId) {
        return repository.findPurchaseProductUuidById(purchaseProductId)
            .orElseThrow(() -> notFound("Purchase product not found"));
    }

    private UUID findReturnUuid(Long returnId) {
        return repository.findReturnUuidById(returnId)
            .orElseThrow(() -> notFound("Purchase return not found"));
    }
}