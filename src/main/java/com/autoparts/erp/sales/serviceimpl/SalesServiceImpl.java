
package com.autoparts.erp.sales.serviceimpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.customer.exception.CustomerErrorCode;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.inventory.exception.InventoryErrorCode;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.service.InventoryService;
import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesPaymentRequest;
import com.autoparts.erp.sales.dto.SalesPaymentResponse;
import com.autoparts.erp.sales.dto.SalesProductRequest;
import com.autoparts.erp.sales.dto.SalesProductResponse;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.exception.SalesErrorCode;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.repository.SalesRepository;
import com.autoparts.erp.sales.service.SalesService;
import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.autoparts.erp.tax.repository.TaxRateRepository;
import java.util.Map;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.audit.service.AuditLogService;

@Service
@SalesAccess
public class SalesServiceImpl implements SalesService {

    private static final Logger logger =
        LoggerFactory.getLogger(SalesServiceImpl.class);

    private static final int MONEY_SCALE = 2;

    private final SalesRepository salesRepository;
    private final InventoryService inventoryService;
    private final ProductTaxMappingRepository productTaxMappingRepository;
    private final TaxRateRepository taxRateRepository;
    private final CustomerRepository customerRepository;
    private final CashRegisterService cashRegisterService;
    private final AuditLogService auditLogService;

    public SalesServiceImpl(
            SalesRepository salesRepository,
            InventoryService inventoryService,
            ProductTaxMappingRepository productTaxMappingRepository,
            TaxRateRepository taxRateRepository,
            CustomerRepository customerRepository,
            CashRegisterService cashRegisterService,
            AuditLogService auditLogService
    ) {
        this.salesRepository = salesRepository;
        this.inventoryService = inventoryService;
        this.productTaxMappingRepository = productTaxMappingRepository;
        this.taxRateRepository = taxRateRepository;
        this.customerRepository = customerRepository;
        this.cashRegisterService = cashRegisterService;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------
    // CREATE SALES DRAFT
    // ---------------------------------------------------------

    @Override
    @Transactional
    public SalesResponse create(SalesCreateRequest request) {

        logger.info(
            "Sales draft creation started for sale date {}",
            request.saleDate()
        );

        validateRequest(request);

        UUID saleUuid = UUID.randomUUID();

        logger.info(
            "Generating invoice number for sale date {}",
            request.saleDate()
        );

        String invoiceNumber =
            salesRepository.generateInvoiceNumber(request.saleDate());

        if (salesRepository.existsInvoiceNumber(invoiceNumber)) {
            logger.warn(
                "Duplicate invoice number generated: {}",
                invoiceNumber
            );

            throw new ApiException(
                SalesErrorCode.SALES_DUPLICATE_INVOICE
            );
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal taxableTotal = BigDecimal.ZERO;

        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        List<SalesProduct> saleProducts = new ArrayList<>();

        for (SalesProductRequest productRequest : request.products()) {

            Long productId = salesRepository
                .findActiveProductId(productRequest.productUuid())
                .orElseThrow(() -> {
                    logger.warn(
                        "Product validation failed for UUID {} during draft creation",
                        productRequest.productUuid()
                    );

                    return new ApiException(
                        SalesErrorCode.PRODUCT_NOT_FOUND
                    );
                });

            InventoryStock product;

            try {
                product = inventoryService.getCurrentStock(productId);
            } catch (ApiException exception) {

                if (exception.getError()
                    == InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND) {

                    logger.warn(
                        "Product stock lookup failed for product ID {} during draft creation",
                        productId
                    );

                    throw new ApiException(
                        SalesErrorCode.PRODUCT_NOT_FOUND
                    );
                }

                throw exception;
            }

            BigDecimal quantity = productRequest.quantity();
            BigDecimal unitPrice = money(productRequest.unitPrice());

            BigDecimal grossAmount = money(
                quantity.multiply(unitPrice)
            );

            BigDecimal discount = money(
                productRequest.discountAmount() == null
                    ? BigDecimal.ZERO
                    : productRequest.discountAmount()
            );

            if (discount.compareTo(grossAmount) > 0) {
                throw new ApiException(
                    SalesErrorCode.INVALID_SALES_DISCOUNT
                );
            }

            BigDecimal taxableAmount = money(
                grossAmount.subtract(discount)
            );

            ProductTaxMapping mapping =
                productTaxMappingRepository
                    .findMappingByProductUuidAndDate(
                        productRequest.productUuid(),
                        request.saleDate()
                    )
                    .orElseThrow(() -> {
                        logger.warn(
                            "Tax mapping not found for product UUID {} on sale date {}",
                            productRequest.productUuid(),
                            request.saleDate()
                        );

                        return new ApiException(
                            SalesErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND
                        );
                    });

            TaxRate taxRate = taxRateRepository
                .findByUuid(mapping.taxRateUuid())
                .orElseThrow(() ->
                    new ApiException(
                        SalesErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND
                    )
                );

            BigDecimal gstRate = taxRate.gstRate();
            BigDecimal cgstRate = taxRate.cgstRate();
            BigDecimal sgstRate = taxRate.sgstRate();
            BigDecimal igstRate = taxRate.igstRate();

            BigDecimal cgstAmount = BigDecimal.ZERO;
            BigDecimal sgstAmount = BigDecimal.ZERO;
            BigDecimal igstAmount = BigDecimal.ZERO;

            if (request.supplyType().name().equals("INTRA_STATE")) {

                cgstAmount = calculateTax(taxableAmount, cgstRate);
                sgstAmount = calculateTax(taxableAmount, sgstRate);

            } else {

                igstAmount = calculateTax(taxableAmount, igstRate);
            }

            BigDecimal lineTax = money(
                cgstAmount.add(sgstAmount).add(igstAmount)
            );

            BigDecimal lineTotal = money(
                taxableAmount.add(lineTax)
            );

            SalesProduct saleProduct = new SalesProduct(
                null,
                UUID.randomUUID(),
                null,
                productId,
                product.productUuid(),
                product.productCode(),
                product.productName(),
                quantity,
                product.unit(),
                unitPrice,
                discount,
                taxableAmount,
                mapping.hsnCode(),
                gstRate,
                cgstRate,
                sgstRate,
                igstRate,
                cgstAmount,
                sgstAmount,
                igstAmount,
                lineTax,
                lineTotal,
                null
            );

            saleProducts.add(saleProduct);

            subtotal = subtotal.add(grossAmount);
            totalDiscount = totalDiscount.add(discount);
            taxableTotal = taxableTotal.add(taxableAmount);

            cgstTotal = cgstTotal.add(cgstAmount);
            sgstTotal = sgstTotal.add(sgstAmount);
            igstTotal = igstTotal.add(igstAmount);

            taxTotal = taxTotal.add(lineTax);
            grandTotal = grandTotal.add(lineTotal);
        }

        subtotal = money(subtotal);
        totalDiscount = money(totalDiscount);
        taxableTotal = money(taxableTotal);
        cgstTotal = money(cgstTotal);
        sgstTotal = money(sgstTotal);
        igstTotal = money(igstTotal);
        taxTotal = money(taxTotal);
        grandTotal = money(grandTotal);

        BigDecimal paidAmount = calculatePaymentTotal(
            request.payments()
        );

        if (paidAmount.compareTo(grandTotal) > 0) {
            logger.warn(
                "Payment validation failed for sales draft: paid amount {} exceeds total {}",
                paidAmount,
                grandTotal
            );

            throw new ApiException(
                SalesErrorCode.SALES_PAYMENT_EXCEEDS_TOTAL
            );
        }

        BigDecimal balanceAmount = money(
            grandTotal.subtract(paidAmount)
        );

        SalesPaymentStatus paymentStatus =
            calculatePaymentStatus(paidAmount, grandTotal);

        Long customerId = null;
        String customerName = null;
        String customerPhone = null;

        if (request.customerUuid() != null) {

            Customer customer = customerRepository
                .findByUuid(request.customerUuid())
                .orElseThrow(() ->
                    new ApiException(
                        CustomerErrorCode.CUSTOMER_NOT_FOUND
                    )
                );

            customerId = customer.id();
            customerName = customer.customerName();
            customerPhone = customer.phone();
        }

        Sales sale = new Sales(
            null,
            saleUuid,
            invoiceNumber,
            customerId,
            customerName,
            customerPhone,
            request.saleDate(),
            subtotal,
            totalDiscount,
            taxableTotal,
            cgstTotal,
            sgstTotal,
            igstTotal,
            taxTotal,
            grandTotal,
            paidAmount,
            balanceAmount,
            SalesStatus.DRAFT,
            paymentStatus,
            request.notes(),
            null,
            null
        );

        Sales createdSale = salesRepository.createSale(sale);

        for (SalesProduct product : saleProducts) {

            SalesProduct savedProduct = new SalesProduct(
                null,
                product.uuid(),
                createdSale.id(),
                product.productId(),
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
                product.lineTotal(),
                null
            );

            salesRepository.createSaleProduct(savedProduct);
        }

        // Save draft payments only.
        // Cash Register movements are posted when the sale is completed.
        if (request.payments() != null) {

            for (var paymentRequest : request.payments()) {

                SalesPayment payment = new SalesPayment(
                    null,
                    UUID.randomUUID(),
                    createdSale.id(),
                    paymentRequest.paymentMethod(),
                    money(paymentRequest.amount()),
                    paymentRequest.referenceNumber(),
                    OffsetDateTime.now(),
                    paymentRequest.notes(),
                    null
                );

                salesRepository.createSalePayment(payment);
            }
        }

        logger.info(
            "Sales draft successfully created with invoice number {}",
            invoiceNumber
        );
        
        auditLogService.log(
                "CREATE",
                "SALES",
                createdSale.invoiceNumber(),
                "Sales draft created",
                null,
                Map.of(
                        "saleUuid", createdSale.uuid().toString(),
                        "invoiceNumber", createdSale.invoiceNumber(),
                        "status", createdSale.status().name(),
                        "grandTotal", grandTotal,
                        "paidAmount", paidAmount
                )
        );

        return findByUuid(createdSale.uuid());
    }

    // ---------------------------------------------------------
    // COMPLETE SALES
    // ---------------------------------------------------------

    @Override
    @Transactional
    public SalesResponse complete(UUID saleUuid) {

        logger.info(
            "Sales invoice completion started for UUID {}",
            saleUuid
        );

        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() ->
                new ApiException(SalesErrorCode.SALES_NOT_FOUND)
            );

        if (sale.status() == SalesStatus.COMPLETED) {
            throw new ApiException(
                SalesErrorCode.SALES_ALREADY_COMPLETED
            );
        }

        if (sale.status() == SalesStatus.CANCELLED) {
            throw new ApiException(
                SalesErrorCode.SALES_ALREADY_CANCELLED
            );
        }

        List<SalesProduct> saleProducts =
            salesRepository.findProductsBySaleId(sale.id());

        if (saleProducts == null || saleProducts.isEmpty()) {
            throw new ApiException(
                SalesErrorCode.INVALID_SALES_AMOUNT
            );
        }

        List<SalesProduct> sortedProducts = saleProducts.stream()
            .sorted(Comparator.comparing(SalesProduct::productId))
            .toList();

        // Initial payments were stored during draft creation.
        // Post CASH payments only when the sale is being completed.
        List<SalesPayment> payments =
            salesRepository.findPaymentsBySaleId(sale.id());

        for (SalesPayment payment : payments) {

            if (payment.paymentMethod() != PaymentMethod.CASH) {
                continue;
            }

            cashRegisterService.recordTransactionMovement(
                CashMovementType.IN,
                CashMovementSourceType.SALE_PAYMENT,
                payment.uuid(),
                payment.amount(),
                "Cash received for invoice " + sale.invoiceNumber()
            );
        }

        // Update inventory.
        for (SalesProduct product : sortedProducts) {

            try {
                inventoryService.decreaseStock(
                    product.productId(),
                    product.quantity(),
                    sale.id()
                );

            } catch (ApiException exception) {

                if (exception.getError()
                    == InventoryErrorCode.INVENTORY_PRODUCT_NOT_FOUND) {

                    logger.warn(
                        "Product stock validation failed for product ID {} during completion",
                        product.productId()
                    );

                    throw new ApiException(
                        SalesErrorCode.PRODUCT_NOT_FOUND
                    );
                }

                if (exception.getError()
                    == InventoryErrorCode.INSUFFICIENT_STOCK) {

                    throw new ApiException(
                        SalesErrorCode.INSUFFICIENT_STOCK
                    );
                }

                throw exception;
            }
        }

        Sales completedSale = new Sales(
            sale.id(),
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerId(),
            sale.customerName(),
            sale.customerPhone(),
            sale.saleDate(),
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            sale.paidAmount(),
            sale.balanceAmount(),
            SalesStatus.COMPLETED,
            sale.paymentStatus(),
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(completedSale);
        
        auditLogService.log(
                "APPROVE",
                "SALES",
                completedSale.invoiceNumber(),
                "Sales invoice completed",
                Map.of(
                        "status", sale.status().name()
                ),
                Map.of(
                        "status", completedSale.status().name(),
                        "grandTotal", completedSale.grandTotal()
                )
        );

        logger.info(
            "Sales invoice completion successful for UUID {}",
            saleUuid
        );

        return findByUuid(saleUuid);
    }

    // ---------------------------------------------------------
    // FIND SALES BY UUID
    // ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SalesResponse findByUuid(UUID saleUuid) {

        Sales sale = salesRepository.findByUuid(saleUuid)
            .orElseThrow(() ->
                new ApiException(SalesErrorCode.SALES_NOT_FOUND)
            );

        List<SalesProductResponse> productResponses =
            salesRepository.findProductsBySaleId(sale.id())
                .stream()
                .map(product -> new SalesProductResponse(
                    product.uuid(),
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
                ))
                .toList();

        List<SalesPaymentResponse> paymentResponses =
            salesRepository.findPaymentsBySaleId(sale.id())
                .stream()
                .map(payment -> new SalesPaymentResponse(
                    payment.uuid(),
                    payment.paymentMethod(),
                    payment.amount(),
                    payment.referenceNumber(),
                    payment.paymentDate(),
                    payment.notes()
                ))
                .toList();

        return toResponse(sale, productResponses, paymentResponses);
    }

    // ---------------------------------------------------------
    // FIND ALL SALES
    // ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SalesResponse> findAll() {

        return salesRepository.findAll()
            .stream()
            .map(sale -> toResponse(
                sale,
                salesRepository.findProductsBySaleId(sale.id())
                    .stream()
                    .map(product -> new SalesProductResponse(
                        product.uuid(),
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
                    ))
                    .toList(),
                salesRepository.findPaymentsBySaleId(sale.id())
                    .stream()
                    .map(payment -> new SalesPaymentResponse(
                        payment.uuid(),
                        payment.paymentMethod(),
                        payment.amount(),
                        payment.referenceNumber(),
                        payment.paymentDate(),
                        payment.notes()
                    ))
                    .toList()
            ))
            .toList();
    }

    // ---------------------------------------------------------
    // CANCEL SALES DRAFT
    // ---------------------------------------------------------

    @Override
    @AdminOrManager
    @Transactional
    public SalesResponse cancel(UUID saleUuid) {

        logger.info(
            "Sales cancellation started for UUID {}",
            saleUuid
        );

        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() ->
                new ApiException(SalesErrorCode.SALES_NOT_FOUND)
            );

        if (sale.status() == SalesStatus.COMPLETED) {
            // Completed invoices require a return/refund workflow.
            throw new ApiException(
                SalesErrorCode.SALES_ALREADY_COMPLETED
            );
        }

        if (sale.status() == SalesStatus.CANCELLED) {
            throw new ApiException(
                SalesErrorCode.SALES_ALREADY_CANCELLED
            );
        }

        Sales cancelledSale = new Sales(
            sale.id(),
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerId(),
            sale.customerName(),
            sale.customerPhone(),
            sale.saleDate(),
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            sale.paidAmount(),
            sale.balanceAmount(),
            SalesStatus.CANCELLED,
            sale.paymentStatus(),
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(cancelledSale);
        
        auditLogService.log(
                "CANCEL",
                "SALES",
                cancelledSale.invoiceNumber(),
                "Sales draft cancelled",
                Map.of(
                        "status", sale.status().name()
                ),
                Map.of(
                        "status", cancelledSale.status().name()
                )
        );

        logger.info(
            "Sales cancellation successful for UUID {}",
            saleUuid
        );

        return findByUuid(saleUuid);
    }

    // ---------------------------------------------------------
    // BUILD SALES RESPONSE
    // ---------------------------------------------------------

    private SalesResponse toResponse(
        Sales sale,
        List<SalesProductResponse> products,
        List<SalesPaymentResponse> payments
    ) {

        return new SalesResponse(
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerName(),
            sale.customerPhone(),
            sale.saleDate(),
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            sale.paidAmount(),
            sale.balanceAmount(),
            sale.status(),
            sale.paymentStatus(),
            sale.notes(),
            products,
            payments,
            sale.createdAt(),
            sale.updatedAt()
        );
    }

    // ---------------------------------------------------------
    // VALIDATE SALES REQUEST
    // ---------------------------------------------------------

    private void validateRequest(SalesCreateRequest request) {

        if (request.products() == null ||
            request.products().isEmpty()) {

            throw new ApiException(
                SalesErrorCode.INVALID_SALES_AMOUNT
            );
        }
    }

    // ---------------------------------------------------------
    // CALCULATE PAYMENT TOTAL
    // ---------------------------------------------------------

    private BigDecimal calculatePaymentTotal(
        List<SalesPaymentRequest> payments
    ) {

        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        BigDecimal total = payments.stream()
            .map(payment -> money(payment.amount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return money(total);
    }

    // ---------------------------------------------------------
    // CALCULATE PAYMENT STATUS
    // ---------------------------------------------------------

    private SalesPaymentStatus calculatePaymentStatus(
        BigDecimal paidAmount,
        BigDecimal grandTotal
    ) {

        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            return SalesPaymentStatus.UNPAID;
        }

        if (paidAmount.compareTo(grandTotal) >= 0) {
            return SalesPaymentStatus.PAID;
        }

        return SalesPaymentStatus.PARTIALLY_PAID;
    }

    // ---------------------------------------------------------
    // CALCULATE GST
    // ---------------------------------------------------------

    private BigDecimal calculateTax(
        BigDecimal taxableAmount,
        BigDecimal taxRate
    ) {

        return money(
            taxableAmount
                .multiply(taxRate)
                .divide(
                    new BigDecimal("100"),
                    6,
                    RoundingMode.HALF_UP
                )
        );
    }

    // ---------------------------------------------------------
    // MONEY ROUNDING
    // ---------------------------------------------------------

    private BigDecimal money(BigDecimal amount) {

        return amount.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
        );
    }

    // ---------------------------------------------------------
    // RECORD ADDITIONAL SALES PAYMENT
    // ---------------------------------------------------------

    @Override
    @Transactional
    public SalesResponse recordPayment(
        UUID saleUuid,
        SalesPaymentRequest request
    ) {

        logger.info(
            "Recording payment for sales invoice UUID {}",
            saleUuid
        );

        // 1. Lock the invoice to prevent concurrent payment updates.
        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() ->
                new ApiException(SalesErrorCode.SALES_NOT_FOUND)
            );

        // 2. Only completed invoices can receive additional payments.
        if (sale.status() != SalesStatus.COMPLETED) {
            throw new ApiException(
                SalesErrorCode.SALES_PAYMENT_NOT_ALLOWED
            );
        }

        // 3. Validate payment amount.
        if (request.amount() == null ||
            request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new ApiException(
                SalesErrorCode.INVALID_SALES_PAYMENT
            );
        }

        if (request.paymentMethod() == null) {
            throw new ApiException(
                SalesErrorCode.INVALID_SALES_PAYMENT
            );
        }

        BigDecimal paymentAmount = money(request.amount());

        // 4. Prevent overpayment.
        if (paymentAmount.compareTo(sale.balanceAmount()) > 0) {
            throw new ApiException(
                SalesErrorCode.SALES_PAYMENT_EXCEEDS_BALANCE
            );
        }

        // 5. Calculate updated invoice amounts.
        BigDecimal paidAmount = money(
            sale.paidAmount().add(paymentAmount)
        );

        BigDecimal balanceAmount = money(
            sale.grandTotal().subtract(paidAmount)
        );

        SalesPaymentStatus paymentStatus =
            calculatePaymentStatus(
                paidAmount,
                sale.grandTotal()
            );

        // 6. Create payment record.
        SalesPayment payment = new SalesPayment(
            null,
            UUID.randomUUID(),
            sale.id(),
            request.paymentMethod(),
            paymentAmount,
            request.referenceNumber(),
            OffsetDateTime.now(),
            request.notes(),
            null
        );

        salesRepository.createSalePayment(payment);

        // 7. Record physical cash only for CASH payments.
        // This is part of the same database transaction.
        if (payment.paymentMethod() == PaymentMethod.CASH) {

            cashRegisterService.recordTransactionMovement(
                CashMovementType.IN,
                CashMovementSourceType.SALE_PAYMENT,
                payment.uuid(),
                payment.amount(),
                "Cash received for invoice " + sale.invoiceNumber()
            );
        }

        // 8. Update invoice payment information.
        Sales updatedSale = new Sales(
            sale.id(),
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerId(),
            sale.customerName(),
            sale.customerPhone(),
            sale.saleDate(),
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            paidAmount,
            balanceAmount,
            sale.status(),
            paymentStatus,
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(updatedSale);
        
        auditLogService.log(
                "CREATE",
                "SALES_PAYMENT",
                payment.uuid().toString(),
                "Additional sales payment recorded",
                Map.of(
                        "paidAmount", sale.paidAmount(),
                        "balanceAmount", sale.balanceAmount()
                ),
                Map.of(
                        "paymentAmount", paymentAmount,
                        "paidAmount", paidAmount,
                        "balanceAmount", balanceAmount,
                        "paymentMethod", request.paymentMethod().name()
                )
        );

        logger.info(
            "Payment recorded successfully for invoice {}",
            sale.invoiceNumber()
        );

        // 9. Return updated invoice with payment history.
        return findByUuid(saleUuid);
    }
}
