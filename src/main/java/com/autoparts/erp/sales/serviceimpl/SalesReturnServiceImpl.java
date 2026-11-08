
package com.autoparts.erp.sales.serviceimpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.inventory.service.InventoryService;
import com.autoparts.erp.sales.dto.SalesRefundRequest;
import com.autoparts.erp.sales.dto.SalesRefundResponse;
import com.autoparts.erp.sales.dto.SalesReturnProductRequest;
import com.autoparts.erp.sales.dto.SalesReturnProductResponse;
import com.autoparts.erp.sales.dto.SalesReturnRequest;
import com.autoparts.erp.sales.dto.SalesReturnResponse;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesReturnStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesRefund;
import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.repository.SalesRepository;
import com.autoparts.erp.sales.repository.SalesReturnRepository;
import com.autoparts.erp.sales.service.SalesReturnService;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;

@Service
@SalesAccess
public class SalesReturnServiceImpl implements SalesReturnService {

    private static final Logger logger =
            LoggerFactory.getLogger(SalesReturnServiceImpl.class);

    private static final int MONEY_SCALE = 2;

    private static final RoundingMode ROUNDING =
            RoundingMode.HALF_UP;

    private final SalesRepository salesRepository;
    private final SalesReturnRepository returnRepository;
    private final InventoryService inventoryService;
    private final CashRegisterService cashRegisterService;
    private final AuditLogService auditLogService;

    public SalesReturnServiceImpl(
            SalesRepository salesRepository,
            SalesReturnRepository returnRepository,
            InventoryService inventoryService,
            CashRegisterService cashRegisterService,
            AuditLogService auditLogService
    ) {
        this.salesRepository = salesRepository;
        this.returnRepository = returnRepository;
        this.inventoryService = inventoryService;
        this.cashRegisterService = cashRegisterService;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------
    // CREATE SALES RETURN
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @Transactional
    public SalesReturnResponse createReturn(
            UUID saleUuid,
            SalesReturnRequest request
    ) {

        logger.info(
                "Sales return creation started for sale UUID {}",
                saleUuid
        );

        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
                .orElseThrow(() ->
                        notFound("Sales invoice not found")
                );

        if (sale.status() != SalesStatus.COMPLETED) {
            throw badRequest(
                    "Only completed sales invoices can be returned"
            );
        }

        if (request.products() == null || request.products().isEmpty()) {
            throw badRequest(
                    "At least one return product is required"
            );
        }

        List<SalesProduct> originalProducts =
                salesRepository.findProductsBySaleId(sale.id());

        SalesReturn salesReturn = new SalesReturn(
                null,
                UUID.randomUUID(),
                sale.id(),
                OffsetDateTime.now(),
                request.reason(),
                request.notes(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                SalesReturnStatus.ACCEPTED,
                null,
                null
        );

        salesReturn = returnRepository.createReturn(salesReturn);

        Set<UUID> processedSalesProductUuids = new HashSet<>();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalReturn = BigDecimal.ZERO;

        for (SalesReturnProductRequest productRequest : request.products()) {

            if (!processedSalesProductUuids.add(
                    productRequest.salesProductUuid())) {

                throw badRequest(
                        "Duplicate sales product in return request"
                );
            }

            SalesProduct originalProduct = originalProducts.stream()
                    .filter(product ->
                            product.uuid().equals(
                                    productRequest.salesProductUuid()
                            )
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            badRequest(
                                    "Product does not belong to this invoice"
                            )
                    );

            BigDecimal requestedQuantity = productRequest.quantity();

            if (requestedQuantity == null
                    || requestedQuantity.compareTo(BigDecimal.ZERO) <= 0) {

                throw badRequest(
                        "Return quantity must be greater than zero"
                );
            }

            List<SalesReturnProduct> previousReturns =
                    returnRepository.findReturnedProductsBySaleProductId(
                            originalProduct.id()
                    );

            BigDecimal previouslyReturnedQuantity =
                    previousReturns.stream()
                            .map(SalesReturnProduct::quantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal remainingQuantity =
                    originalProduct.quantity()
                            .subtract(previouslyReturnedQuantity);

            if (requestedQuantity.compareTo(remainingQuantity) > 0) {

                throw badRequest(
                        "Return quantity exceeds the remaining sold quantity for product "
                                + originalProduct.productCode()
                );
            }

            boolean finalReturn =
                    requestedQuantity.compareTo(remainingQuantity) == 0;

            SalesReturnProduct returnProduct =
                    calculateReturnProduct(
                            salesReturn.id(),
                            originalProduct,
                            requestedQuantity,
                            previousReturns,
                            finalReturn
                    );

            returnRepository.createReturnProduct(returnProduct);

            // Restore inventory using the original numeric product ID.
            inventoryService.restoreStockFromSalesReturn(
                    originalProduct.productId(),
                    requestedQuantity,
                    salesReturn.id()
            );

            subtotal = subtotal.add(
                    returnProduct.unitPrice().multiply(requestedQuantity)
            );

            discount = discount.add(returnProduct.discountAmount());
            taxable = taxable.add(returnProduct.taxableAmount());
            cgst = cgst.add(returnProduct.cgstAmount());
            sgst = sgst.add(returnProduct.sgstAmount());
            igst = igst.add(returnProduct.igstAmount());
            totalTax = totalTax.add(returnProduct.totalTaxAmount());
            totalReturn = totalReturn.add(returnProduct.lineTotal());
        }

        salesReturn = new SalesReturn(
                salesReturn.id(),
                salesReturn.uuid(),
                salesReturn.saleId(),
                salesReturn.returnDate(),
                salesReturn.reason(),
                salesReturn.notes(),
                money(subtotal),
                money(discount),
                money(taxable),
                money(cgst),
                money(sgst),
                money(igst),
                money(totalTax),
                money(totalReturn),
                SalesReturnStatus.ACCEPTED,
                salesReturn.createdAt(),
                salesReturn.updatedAt()
        );

        returnRepository.updateReturn(salesReturn);

        // Audit log for sales return creation.
        Map<String, Object> returnAuditData = new HashMap<>();

        returnAuditData.put(
                "returnUuid",
                salesReturn.uuid().toString()
        );
        returnAuditData.put("saleId", sale.id());
        returnAuditData.put("invoiceNumber", sale.invoiceNumber());
        returnAuditData.put("reason", salesReturn.reason());
        returnAuditData.put("status", salesReturn.status().name());
        returnAuditData.put(
                "totalReturnAmount",
                salesReturn.totalReturnAmount()
        );
        returnAuditData.put(
                "productCount",
                request.products().size()
        );

        auditLogService.log(
                "CREATE",
                "SALES_RETURN",
                salesReturn.uuid().toString(),
                "Sales return created and stock restored",
                null,
                returnAuditData
        );

        logger.info(
                "Sales return created. Return UUID: {}, invoice UUID: {}",
                salesReturn.uuid(),
                saleUuid
        );

        return buildResponse(salesReturn, sale);
    }

    // ---------------------------------------------------------
    // CALCULATE RETURN PRODUCT
    // ---------------------------------------------------------

    private SalesReturnProduct calculateReturnProduct(
            Long returnId,
            SalesProduct original,
            BigDecimal quantity,
            List<SalesReturnProduct> previousReturns,
            boolean finalReturn
    ) {

        BigDecimal originalQuantity = original.quantity();

        BigDecimal unitPrice = original.unitPrice();

        BigDecimal discountAmount = allocateAmount(
                original.discountAmount(),
                originalQuantity,
                quantity,
                previousReturns,
                SalesReturnProduct::discountAmount,
                finalReturn
        );

        BigDecimal taxableAmount = allocateAmount(
                original.taxableAmount(),
                originalQuantity,
                quantity,
                previousReturns,
                SalesReturnProduct::taxableAmount,
                finalReturn
        );

        BigDecimal cgstAmount = allocateAmount(
                original.cgstAmount(),
                originalQuantity,
                quantity,
                previousReturns,
                SalesReturnProduct::cgstAmount,
                finalReturn
        );

        BigDecimal sgstAmount = allocateAmount(
                original.sgstAmount(),
                originalQuantity,
                quantity,
                previousReturns,
                SalesReturnProduct::sgstAmount,
                finalReturn
        );

        BigDecimal igstAmount = allocateAmount(
                original.igstAmount(),
                originalQuantity,
                quantity,
                previousReturns,
                SalesReturnProduct::igstAmount,
                finalReturn
        );

        BigDecimal totalTaxAmount = cgstAmount
                .add(sgstAmount)
                .add(igstAmount);

        BigDecimal lineTotal = taxableAmount.add(totalTaxAmount);

        return new SalesReturnProduct(
                null,
                UUID.randomUUID(),
                returnId,
                original.id(),
                original.productId(),
                original.productUuid(),
                original.uuid(),
                original.productCode(),
                original.productName(),
                quantity,
                original.unit(),
                unitPrice,
                discountAmount,
                taxableAmount,
                original.hsnCode(),
                original.gstRate(),
                original.cgstRate(),
                original.sgstRate(),
                original.igstRate(),
                cgstAmount,
                sgstAmount,
                igstAmount,
                totalTaxAmount,
                lineTotal,
                null
        );
    }

    // ---------------------------------------------------------
    // ALLOCATE ORIGINAL AMOUNTS TO PARTIAL RETURNS
    // ---------------------------------------------------------

    private BigDecimal allocateAmount(
            BigDecimal originalAmount,
            BigDecimal originalQuantity,
            BigDecimal requestedQuantity,
            List<SalesReturnProduct> previousReturns,
            java.util.function.Function<SalesReturnProduct, BigDecimal> getter,
            boolean finalReturn
    ) {

        BigDecimal previousAmount = previousReturns.stream()
                .map(getter)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (finalReturn) {
            return money(originalAmount.subtract(previousAmount));
        }

        return money(
                originalAmount
                        .multiply(requestedQuantity)
                        .divide(
                                originalQuantity,
                                8,
                                ROUNDING
                        )
        );
    }

    // ---------------------------------------------------------
    // FIND RETURNS BY SALES UUID
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SalesReturnResponse> findReturnsBySaleUuid(
            UUID saleUuid
    ) {

        Sales sale = salesRepository.findByUuid(saleUuid)
                .orElseThrow(() ->
                        notFound("Sales invoice not found")
                );

        return returnRepository.findReturnsBySaleId(sale.id())
                .stream()
                .map(salesReturn -> buildResponse(salesReturn, sale))
                .toList();
    }

    // ---------------------------------------------------------
    // FIND RETURN BY UUID
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SalesReturnResponse findReturnByUuid(UUID returnUuid) {

        SalesReturn salesReturn = returnRepository.findReturnByUuid(returnUuid)
                .orElseThrow(() ->
                        notFound("Sales return not found")
                );

        // This is a read operation. Do not use FOR UPDATE here.
        Sales sale = salesRepository.findByUuid(
                findSaleUuidForReturn(salesReturn)
        ).orElseThrow(() ->
                notFound("Sales invoice not found")
        );

        return buildResponse(salesReturn, sale);
    }

    // ---------------------------------------------------------
    // FIND ORIGINAL SALE UUID FROM RETURN
    // ---------------------------------------------------------

    private UUID findSaleUuidForReturn(SalesReturn salesReturn) {

        return salesRepository.findUuidById(salesReturn.saleId())
                .orElseThrow(() ->
                        notFound("Sales invoice not found")
                );
    }

    // ---------------------------------------------------------
    // RECORD CUSTOMER REFUND
    // ADMIN / MANAGER ONLY
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOrManager
    public SalesReturnResponse recordRefund(
            UUID returnUuid,
            SalesRefundRequest request
    ) {

        SalesReturn salesReturn =
                returnRepository.findReturnByUuidForUpdate(returnUuid)
                        .orElseThrow(() ->
                                notFound("Sales return not found")
                        );

        BigDecimal refundedAmount =
                returnRepository.getRefundedAmount(salesReturn.id());

        BigDecimal refundableAmount =
                salesReturn.totalReturnAmount().subtract(refundedAmount);

        if (request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw badRequest(
                    "Refund amount must be greater than zero"
            );
        }

        if (request.amount().compareTo(refundableAmount) > 0) {

            throw badRequest(
                    "Refund amount exceeds the remaining refundable amount"
            );
        }

        SalesRefund refund = new SalesRefund(
                null,
                UUID.randomUUID(),
                salesReturn.id(),
                request.paymentMethod(),
                money(request.amount()),
                request.referenceNumber(),
                OffsetDateTime.now(),
                request.notes(),
                null
        );

        // Save refund.
        refund = returnRepository.createRefund(refund);

        // Record cash movement only for CASH refunds.
        if (refund.paymentMethod() == PaymentMethod.CASH) {

            cashRegisterService.recordTransactionMovement(
                    CashMovementType.OUT,
                    CashMovementSourceType.SALES_REFUND,
                    refund.uuid(),
                    refund.amount(),
                    "Sales refund - Return UUID: "
                            + salesReturn.uuid()
            );
        }

        BigDecimal newRefundedAmount =
                refundedAmount.add(refund.amount());

        SalesReturnStatus newStatus =
                newRefundedAmount.compareTo(
                        salesReturn.totalReturnAmount()
                ) >= 0
                        ? SalesReturnStatus.REFUNDED
                        : SalesReturnStatus.PARTIALLY_REFUNDED;

        SalesReturn updatedReturn = new SalesReturn(
                salesReturn.id(),
                salesReturn.uuid(),
                salesReturn.saleId(),
                salesReturn.returnDate(),
                salesReturn.reason(),
                salesReturn.notes(),
                salesReturn.subtotal(),
                salesReturn.discountAmount(),
                salesReturn.taxableAmount(),
                salesReturn.cgstAmount(),
                salesReturn.sgstAmount(),
                salesReturn.igstAmount(),
                salesReturn.totalTaxAmount(),
                salesReturn.totalReturnAmount(),
                newStatus,
                salesReturn.createdAt(),
                OffsetDateTime.now()
        );

        returnRepository.updateReturn(updatedReturn);

        // Audit log for customer refund.
        auditLogService.log(
                "REFUND",
                "SALES_RETURN",
                salesReturn.uuid().toString(),
                "Customer refund recorded",
                Map.of(
                        "previouslyRefundedAmount", refundedAmount,
                        "previousStatus", salesReturn.status().name()
                ),
                Map.of(
                        "refundUuid", refund.uuid().toString(),
                        "refundAmount", refund.amount(),
                        "paymentMethod", refund.paymentMethod().name(),
                        "totalRefundedAmount", newRefundedAmount,
                        "status", newStatus.name()
                )
        );

        Sales sale = salesRepository.findByUuid(
                findSaleUuidForReturn(updatedReturn)
        ).orElseThrow(() ->
                notFound("Sales invoice not found")
        );

        logger.info(
                "Refund recorded for return UUID {}. Amount: {}",
                returnUuid,
                request.amount()
        );

        return buildResponse(updatedReturn, sale);
    }

    // ---------------------------------------------------------
    // BUILD SALES RETURN RESPONSE
    // ---------------------------------------------------------

    private SalesReturnResponse buildResponse(
            SalesReturn salesReturn,
            Sales sale
    ) {

        List<SalesReturnProductResponse> products =
                returnRepository.findProductsByReturnId(salesReturn.id())
                        .stream()
                        .map(this::toProductResponse)
                        .toList();

        List<SalesRefund> refunds =
                returnRepository.findRefundsByReturnId(salesReturn.id());

        List<SalesRefundResponse> refundResponses =
                refunds.stream()
                        .map(this::toRefundResponse)
                        .toList();

        BigDecimal refundedAmount =
                returnRepository.getRefundedAmount(salesReturn.id());

        BigDecimal refundableAmount =
                salesReturn.totalReturnAmount().subtract(refundedAmount);

        return new SalesReturnResponse(
                salesReturn.uuid(),
                sale.uuid(),
                sale.invoiceNumber(),
                salesReturn.returnDate(),
                salesReturn.reason(),
                salesReturn.notes(),
                salesReturn.subtotal(),
                salesReturn.discountAmount(),
                salesReturn.taxableAmount(),
                salesReturn.cgstAmount(),
                salesReturn.sgstAmount(),
                salesReturn.igstAmount(),
                salesReturn.totalTaxAmount(),
                salesReturn.totalReturnAmount(),
                refundedAmount,
                refundableAmount,
                salesReturn.status(),
                products,
                refundResponses,
                salesReturn.createdAt(),
                salesReturn.updatedAt()
        );
    }

    // ---------------------------------------------------------
    // BUILD RETURN PRODUCT RESPONSE
    // ---------------------------------------------------------

    private SalesReturnProductResponse toProductResponse(
            SalesReturnProduct product
    ) {

        return new SalesReturnProductResponse(
                product.uuid(),
                product.salesProductUuid(),
                product.productUuid(),
                product.productCode(),
                product.productName(),
                product.quantity(),
                product.unit(),
                product.unitPrice(),
                product.discountAmount(),
                product.taxableAmount(),
                product.hsnCode(),
                product.gstRate(),
                product.cgstRate(),
                product.sgstRate(),
                product.igstRate(),
                product.cgstAmount(),
                product.sgstAmount(),
                product.igstAmount(),
                product.totalTaxAmount(),
                product.lineTotal()
        );
    }

    // ---------------------------------------------------------
    // BUILD REFUND RESPONSE
    // ---------------------------------------------------------

    private SalesRefundResponse toRefundResponse(SalesRefund refund) {

        return new SalesRefundResponse(
                refund.uuid(),
                refund.paymentMethod(),
                refund.amount(),
                refund.referenceNumber(),
                refund.refundDate(),
                refund.notes(),
                refund.createdAt()
        );
    }

    // ---------------------------------------------------------
    // MONEY ROUNDING
    // ---------------------------------------------------------

    private BigDecimal money(BigDecimal value) {

        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    // ---------------------------------------------------------
    // BAD REQUEST EXCEPTION
    // ---------------------------------------------------------

    private ResponseStatusException badRequest(String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // ---------------------------------------------------------
    // NOT FOUND EXCEPTION
    // ---------------------------------------------------------

    private ResponseStatusException notFound(String message) {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
