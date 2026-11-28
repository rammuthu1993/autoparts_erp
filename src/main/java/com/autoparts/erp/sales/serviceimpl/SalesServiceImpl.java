
package com.autoparts.erp.sales.serviceimpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;

import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.util.PaginationUtils;
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
import com.autoparts.erp.sales.dto.SalesSummaryResponse;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesReturnStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.enums.SupplyType;
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

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import com.autoparts.erp.sales.dto.SalesCancelRequest;
import com.autoparts.erp.sales.dto.SalesCancellationPreviewResponse;
import com.autoparts.erp.sales.dto.SalesCancellationRefundAllocation;
import com.autoparts.erp.sales.dto.SalesCancellationRefundConfirmRequest;
import com.autoparts.erp.sales.dto.SalesCancellationResponse;
import com.autoparts.erp.sales.enums.CancellationRefundStatus;
import com.autoparts.erp.sales.enums.GstAdjustmentType;
import com.autoparts.erp.sales.model.SalesCancellationRefund;
import com.autoparts.erp.sales.model.SalesInvoiceCancellation;
import com.autoparts.erp.sales.model.SalesInvoiceCancellationItem;
import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.repository.SalesCancellationRepository;
import com.autoparts.erp.sales.repository.SalesReturnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private final SalesReturnRepository salesReturnRepository;
    private final SalesCancellationRepository salesCancellationRepository;
    private final DailyClosingRepository dailyClosingRepository;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    @Value("${erp.sales.cancellation.block-closed-daily-period:false}")
    private boolean blockClosedDailyPeriod;

    public SalesServiceImpl(
            SalesRepository salesRepository,
            InventoryService inventoryService,
            ProductTaxMappingRepository productTaxMappingRepository,
            TaxRateRepository taxRateRepository,
            CustomerRepository customerRepository,
            CashRegisterService cashRegisterService,
            AuditLogService auditLogService
    ) {
        this(
            salesRepository,
            inventoryService,
            productTaxMappingRepository,
            taxRateRepository,
            customerRepository,
            cashRegisterService,
            auditLogService,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Autowired
    public SalesServiceImpl(
            SalesRepository salesRepository,
            InventoryService inventoryService,
            ProductTaxMappingRepository productTaxMappingRepository,
            TaxRateRepository taxRateRepository,
            CustomerRepository customerRepository,
            CashRegisterService cashRegisterService,
            AuditLogService auditLogService,
            SalesReturnRepository salesReturnRepository,
            SalesCancellationRepository salesCancellationRepository,
            DailyClosingRepository dailyClosingRepository,
            CurrentUserService currentUserService,
            PermissionService permissionService
    ) {
        this.salesRepository = salesRepository;
        this.inventoryService = inventoryService;
        this.productTaxMappingRepository = productTaxMappingRepository;
        this.taxRateRepository = taxRateRepository;
        this.customerRepository = customerRepository;
        this.cashRegisterService = cashRegisterService;
        this.auditLogService = auditLogService;
        this.salesReturnRepository = salesReturnRepository;
        this.salesCancellationRepository = salesCancellationRepository;
        this.dailyClosingRepository = dailyClosingRepository;
        this.currentUserService = currentUserService;
        this.permissionService = permissionService;
    }

    // ---------------------------------------------------------
    // CREATE SALES DRAFT
    // ---------------------------------------------------------

    @Override
    @Transactional
    @RequiresPermission("SALES.CREATE")
    public SalesResponse create(SalesCreateRequest request) {

        logger.info(
            "Sales draft creation started for sale date {}",
            request.saleDate()
        );

        validateRequest(request);
        validateInitialPayments(request.payments());
        requireConfiguredSalesStatus(SalesStatus.DRAFT);

        UUID saleUuid = UUID.randomUUID();

        // Drafts use a temporary identifier. GST invoice numbers are allocated
        // only when the draft is confirmed/completed.
        String invoiceNumber = "DRF-" + saleUuid.toString().substring(0, 8).toUpperCase();

        CalculatedSaleDraft draft = buildCalculatedDraft(request, saleUuid, invoiceNumber);

        Sales createdSale = salesRepository.createSale(draft.sale());

        for (SalesProduct product : draft.products()) {
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

        logger.info("Sales draft successfully created with invoice number {}", invoiceNumber);

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
                "grandTotal", createdSale.grandTotal(),
                "paidAmount", createdSale.paidAmount()
            )
        );

        return findByUuid(createdSale.uuid());
    }

    @Override
    @Transactional
    @RequiresPermission("SALES.CREATE")
    public SalesResponse updateDraft(UUID saleUuid, SalesCreateRequest request) {
        validateRequest(request);
        validateInitialPayments(request.payments());

        Sales existing = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));

        if (existing.status() == SalesStatus.COMPLETED) {
            throw new ApiException(SalesErrorCode.SALES_ALREADY_COMPLETED);
        }
        if (existing.status() == SalesStatus.CANCELLED) {
            throw new ApiException(SalesErrorCode.SALES_ALREADY_CANCELLED);
        }

        CalculatedSaleDraft calculated = buildCalculatedDraft(request, existing.uuid(), existing.invoiceNumber());
        Sales updatedHeader = new Sales(
            existing.id(),
            existing.uuid(),
            existing.invoiceNumber(),
            calculated.sale().customerId(),
            calculated.sale().customerName(),
            calculated.sale().customerPhone(),
            calculated.sale().customerGstin(),
            calculated.sale().supplyType(),
            calculated.sale().placeOfSupply(),
            calculated.sale().billingAddress(),
            calculated.sale().shippingAddress(),
            calculated.sale().customerReference(),
            calculated.sale().paymentTerms(),
            calculated.sale().dueDate(),
            calculated.sale().saleDate(),
            calculated.sale().subtotal(),
            calculated.sale().discountAmount(),
            calculated.sale().taxableAmount(),
            calculated.sale().cgstAmount(),
            calculated.sale().sgstAmount(),
            calculated.sale().igstAmount(),
            calculated.sale().totalTaxAmount(),
            calculated.sale().grandTotal(),
            calculated.sale().paidAmount(),
            calculated.sale().balanceAmount(),
            SalesStatus.DRAFT,
            calculated.sale().paymentStatus(),
            calculated.sale().notes(),
            existing.createdAt(),
            existing.updatedAt()
        );

        salesRepository.updateSale(updatedHeader);
        salesRepository.deleteDraftProducts(existing.id());
        salesRepository.deleteDraftPayments(existing.id());

        for (SalesProduct product : calculated.products()) {
            salesRepository.createSaleProduct(new SalesProduct(
                null,
                product.uuid(),
                existing.id(),
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
            ));
        }

        if (request.payments() != null) {
            for (var paymentRequest : request.payments()) {
                salesRepository.createSalePayment(new SalesPayment(
                    null,
                    UUID.randomUUID(),
                    existing.id(),
                    paymentRequest.paymentMethod(),
                    money(paymentRequest.amount()),
                    paymentRequest.referenceNumber(),
                    OffsetDateTime.now(),
                    paymentRequest.notes(),
                    null
                ));
            }
        }

        auditLogService.log(
            "UPDATE",
            "SALES",
            existing.invoiceNumber(),
            "Sales draft updated",
            Map.of(
                "grandTotal", existing.grandTotal(),
                "paidAmount", existing.paidAmount(),
                "saleDate", existing.saleDate().toString()
            ),
            Map.of(
                "grandTotal", updatedHeader.grandTotal(),
                "paidAmount", updatedHeader.paidAmount(),
                "saleDate", updatedHeader.saleDate().toString()
            )
        );

        return findByUuid(saleUuid);
    }

    private record CalculatedSaleDraft(Sales sale, List<SalesProduct> products) {}

    private CalculatedSaleDraft buildCalculatedDraft(SalesCreateRequest request, UUID saleUuid, String invoiceNumber) {

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
        BigDecimal calculatedGrandTotal = money(grandTotal);
        grandTotal = calculatedGrandTotal
            .setScale(0, RoundingMode.HALF_UP)
            .setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);

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

        // Keep draft amounts visible. The draft balance is the invoice total
        // less any advance amount recorded with the draft.
        BigDecimal balanceAmount = money(grandTotal.subtract(paidAmount));
        SalesPaymentStatus paymentStatus = calculatePaymentStatus(paidAmount, grandTotal);
        requireConfiguredPaymentStatus(paymentStatus);

        Long customerId = null;
        String customerName = normalizeOptionalText(request.customerName());
        String customerPhone = normalizeOptionalText(request.customerPhone());
        String customerGstin = normalizeOptionalText(request.customerGstin());
        String billingAddress = normalizeOptionalText(request.billingAddress());
        String shippingAddress = normalizeOptionalText(request.shippingAddress());
        String placeOfSupply = normalizeOptionalText(request.placeOfSupply());

        if (request.customerUuid() != null) {
            Customer customer = customerRepository
                .findByUuid(request.customerUuid())
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

            customerId = customer.id();
            customerName = customer.customerName();
            customerPhone = customer.phone();
            if (customerGstin == null) customerGstin = normalizeOptionalText(customer.gstin());
            if (billingAddress == null) billingAddress = normalizeOptionalText(customer.address());
            if (placeOfSupply == null) placeOfSupply = normalizeOptionalText(customer.state());
        }

        Sales sale = new Sales(
            null,
            saleUuid,
            invoiceNumber,
            customerId,
            customerName,
            customerPhone,
            customerGstin,
            request.supplyType(),
            placeOfSupply,
            billingAddress,
            shippingAddress,
            normalizeOptionalText(request.customerReference()),
            normalizeOptionalText(request.paymentTerms()),
            request.dueDate(),
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
            normalizeOptionalText(request.notes()),
            null,
            null
        );

        return new CalculatedSaleDraft(sale, saleProducts);
    }

    // ---------------------------------------------------------
    // COMPLETE SALES
    // ---------------------------------------------------------

    @Override
    @Transactional
    @RequiresPermission("SALES.COMPLETE")
    public SalesResponse complete(UUID saleUuid) {

        logger.info(
            "Sales invoice completion started for UUID {}",
            saleUuid
        );
        requireConfiguredSalesStatus(SalesStatus.COMPLETED);

        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() ->
                new ApiException(SalesErrorCode.SALES_NOT_FOUND)
            );

        requireConfiguredSalesStatus(SalesStatus.COMPLETED);
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

        BigDecimal issuedBalanceAmount = money(sale.grandTotal().subtract(sale.paidAmount()));
        SalesPaymentStatus issuedPaymentStatus =
            calculatePaymentStatus(sale.paidAmount(), sale.grandTotal());
        requireConfiguredPaymentStatus(issuedPaymentStatus);

        String finalInvoiceNumber = salesRepository.generateInvoiceNumber(sale.saleDate());
        if (salesRepository.existsInvoiceNumber(finalInvoiceNumber)) {
            throw new ApiException(SalesErrorCode.SALES_DUPLICATE_INVOICE);
        }

        Sales completedSale = new Sales(
            sale.id(),
            sale.uuid(),
            finalInvoiceNumber,
            sale.customerId(),
            sale.customerName(),
            sale.customerPhone(),
            sale.customerGstin(),
            sale.supplyType(),
            sale.placeOfSupply(),
            sale.billingAddress(),
            sale.shippingAddress(),
            sale.customerReference(),
            sale.paymentTerms(),
            sale.dueDate(),
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
            issuedBalanceAmount,
            SalesStatus.COMPLETED,
            issuedPaymentStatus,
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
    @RequiresPermission("SALES.VIEW")
    public SalesResponse findByUuid(UUID saleUuid) {
        Sales sale = salesRepository.findByUuid(saleUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));
        return buildResponses(List.of(sale)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("SALES.VIEW")
    public List<SalesResponse> findAll() {
        return buildResponses(salesRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("SALES.VIEW")
    public PageResponse<SalesResponse> findPaged(
        int page,
        int size,
        String search,
        String status,
        String paymentStatus,
        LocalDate fromDate,
        LocalDate toDate,
        UUID customerUuid,
        String paymentMethod,
        String returnStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String sortBy,
        String sortDir
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(SalesErrorCode.INVALID_SALES_AMOUNT);
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<Sales> salesList = salesRepository.findPaged(
            offset, safeSize, search, status, paymentStatus,
            fromDate, toDate, customerUuid, paymentMethod, returnStatus,
            minAmount, maxAmount, sortBy, sortDir
        );

        List<SalesResponse> content = buildResponses(salesList);

        long total = salesRepository.countPaged(
            search, status, paymentStatus, fromDate, toDate, customerUuid,
            paymentMethod, returnStatus, minAmount, maxAmount
        );
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("SALES.VIEW")
    public SalesSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(SalesErrorCode.INVALID_SALES_AMOUNT);
        }
        return salesRepository.getSummary(fromDate, toDate);
    }

    private List<SalesResponse> buildResponses(List<Sales> salesList) {
        if (salesList == null || salesList.isEmpty()) {
            return List.of();
        }
        List<Long> saleIds = salesList.stream().map(Sales::id).toList();
        List<Long> customerIds = salesList.stream()
            .map(Sales::customerId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, List<SalesProduct>> productsBySale = salesRepository.findProductsBySaleIds(saleIds);
        Map<Long, List<SalesPayment>> paymentsBySale = salesRepository.findPaymentsBySaleIds(saleIds);
        Map<Long, String> cancelReasons = salesRepository.findCancellationReasonsBySaleIds(saleIds);
        Map<Long, UUID> customerUuids = salesRepository.findCustomerUuidsByIds(customerIds);
        Map<Long, SalesRepository.SaleReturnAggregate> returnAggs = salesRepository.findReturnAggregatesBySaleIds(saleIds);

        return salesList.stream().map(sale -> {
            List<SalesProductResponse> productResponses = productsBySale
                .getOrDefault(sale.id(), Collections.emptyList())
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

            List<SalesPaymentResponse> paymentResponses = paymentsBySale
                .getOrDefault(sale.id(), Collections.emptyList())
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

            SalesRepository.SaleReturnAggregate agg = returnAggs.get(sale.id());
            SalesReturnStatus retStatus = SalesReturnStatus.NO_RETURNS;
            BigDecimal returnedAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);
            BigDecimal refundedAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);
            BigDecimal outstandingRefund = BigDecimal.ZERO.setScale(MONEY_SCALE);
            if (agg != null && agg.returnedQuantityLines() > 0) {
                returnedAmount = money(agg.returnedAmount());
                refundedAmount = money(agg.refundedAmount());
                outstandingRefund = money(returnedAmount.subtract(refundedAmount).max(BigDecimal.ZERO));
                retStatus = (agg.totalLines() > 0 && agg.fullyReturnedLines() >= agg.totalLines())
                    ? SalesReturnStatus.FULLY_RETURNED
                    : SalesReturnStatus.PARTIALLY_RETURNED;
            }

            UUID customerUuid = sale.customerId() != null ? customerUuids.get(sale.customerId()) : null;
            String cancellationReason = cancelReasons.get(sale.id());

            return toResponse(
                sale,
                customerUuid,
                cancellationReason,
                retStatus,
                returnedAmount,
                refundedAmount,
                outstandingRefund,
                productResponses,
                paymentResponses
            );
        }).toList();
    }

    // ---------------------------------------------------------
    // CANCEL SALES DRAFT
    // ---------------------------------------------------------

    @Override
    @AdminOrManager
    @Transactional
    @RequiresPermission("SALES.CANCEL")
    public SalesResponse cancel(UUID saleUuid, String reason) {
        return cancel(saleUuid, new SalesCancelRequest(reason));
    }

    @Override
    @AdminOrManager
    @Transactional
    @RequiresPermission("SALES.CANCEL")
    public SalesResponse cancel(UUID saleUuid, SalesCancelRequest request) {

        logger.info("Sales cancellation started for UUID {}", saleUuid);
        requireConfiguredSalesStatus(SalesStatus.CANCELLED);
        requireConfiguredPaymentStatus(SalesPaymentStatus.UNPAID);

        if (request == null) {
            throw new ApiException(SalesErrorCode.INVALID_CANCELLATION_REASON);
        }

        String trimmedReason = request.reason() != null ? request.reason().trim() : "";
        if (trimmedReason.isEmpty() || trimmedReason.length() > 500) {
            throw new ApiException(SalesErrorCode.INVALID_CANCELLATION_REASON);
        }

        String idempotencyKey = normalizeOptionalText(request.idempotencyKey());
        if (idempotencyKey != null && salesCancellationRepository != null) {
            var existingByKey = salesCancellationRepository.findByIdempotencyKey(idempotencyKey);
            if (existingByKey.isPresent()) {
                Sales lockedSale = salesRepository.findByUuidForUpdate(saleUuid)
                    .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));
                if (existingByKey.get().saleId().equals(lockedSale.id())) {
                    logger.info("Idempotent replay for sales cancellation UUID {} with key {}", saleUuid, idempotencyKey);
                    return findByUuid(saleUuid);
                }
                throw new ApiException(SalesErrorCode.SALES_CANCELLATION_CONFLICT);
            }
        }

        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));

        if (sale.status() == SalesStatus.CANCELLED) {
            throw new ApiException(SalesErrorCode.SALES_ALREADY_CANCELLED);
        }

        if (sale.status() == SalesStatus.DRAFT) {
            return cancelDraftSale(sale, trimmedReason);
        }

        if (sale.status() != SalesStatus.COMPLETED) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_CONFLICT);
        }

        return cancelIssuedSale(sale, trimmedReason, request, idempotencyKey);
    }

    private SalesResponse cancelDraftSale(Sales sale, String reason) {
        Sales cancelledSale = new Sales(
            sale.id(),
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerId(),
            sale.customerName(),
            sale.customerPhone(),
            sale.customerGstin(),
            sale.supplyType(),
            sale.placeOfSupply(),
            sale.billingAddress(),
            sale.shippingAddress(),
            sale.customerReference(),
            sale.paymentTerms(),
            sale.dueDate(),
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
            BigDecimal.ZERO.setScale(MONEY_SCALE),
            SalesStatus.CANCELLED,
            SalesPaymentStatus.UNPAID,
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(cancelledSale);
        salesRepository.saveCancellationReason(sale.id(), reason);

        auditLogService.log(
            "CANCEL",
            "SALES",
            cancelledSale.invoiceNumber(),
            "Sales draft cancelled: " + reason,
            Map.of("status", sale.status().name()),
            Map.of("status", cancelledSale.status().name())
        );

        logger.info("Sales draft cancellation successful for UUID {}", sale.uuid());
        return findByUuid(sale.uuid());
    }

    private SalesResponse cancelIssuedSale(
        Sales sale,
        String reason,
        SalesCancelRequest request,
        String idempotencyKey
    ) {
        enforceCancelIssuedPermission();

        if (salesCancellationRepository == null || salesReturnRepository == null || currentUserService == null) {
            throw new IllegalStateException("Issued invoice cancellation dependencies are not configured");
        }

        if (salesCancellationRepository.findBySaleId(sale.id()).isPresent()) {
            throw new ApiException(SalesErrorCode.SALES_ALREADY_CANCELLED);
        }

        if (blockClosedDailyPeriod
            && dailyClosingRepository != null
            && dailyClosingRepository.findByBusinessDate(sale.saleDate()).isPresent()) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_PERIOD_LOCKED);
        }

        List<SalesProduct> saleProducts = salesRepository.findProductsBySaleId(sale.id());
        List<SalesPayment> payments = salesRepository.findPaymentsBySaleId(sale.id());
        validateFinancialConsistency(sale, payments);

        List<SalesReturn> previousReturns = salesReturnRepository.findReturnsBySaleId(sale.id());
        BigDecimal previouslyPaidAmount = money(sale.paidAmount());
        BigDecimal previouslyReturnedAmount = money(previousReturns.stream()
            .map(SalesReturn::totalReturnAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal previouslyRefundedAmount = money(salesReturnRepository.getTotalRefundedAmountBySaleId(sale.id()));

        if (previouslyReturnedAmount.compareTo(sale.grandTotal()) > 0
            || previouslyRefundedAmount.compareTo(previouslyPaidAmount) > 0) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_CONFLICT);
        }

        BigDecimal cancelledInvoiceAmount = money(sale.grandTotal().subtract(previouslyReturnedAmount).max(BigDecimal.ZERO));
        BigDecimal refundableAmount = money(previouslyPaidAmount.subtract(previouslyRefundedAmount).max(BigDecimal.ZERO));
        BigDecimal receivableAdjustmentAmount = money(cancelledInvoiceAmount.subtract(refundableAmount).max(BigDecimal.ZERO));

        List<ItemCancellationPlan> itemPlans = buildItemCancellationPlans(saleProducts);
        List<RefundPlan> refundPlans = buildRefundPlans(sale, payments, refundableAmount, request);

        Long currentUserId = currentUserService.getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();

        CancellationRefundStatus overallRefundStatus = determineOverallRefundStatus(refundableAmount, refundPlans);
        PaymentMethod primaryRefundMethod = refundPlans.size() == 1 ? refundPlans.get(0).paymentMethod() : null;
        String primaryRefundRef = refundPlans.size() == 1 ? refundPlans.get(0).referenceNumber() : normalizeOptionalText(request.refundReference());
        OffsetDateTime confirmedAt = overallRefundStatus == CancellationRefundStatus.SUCCESSFUL ? now : null;
        Long confirmedBy = overallRefundStatus == CancellationRefundStatus.SUCCESSFUL ? currentUserId : null;

        GstAdjustmentType gstAdjustmentType = previouslyReturnedAmount.compareTo(BigDecimal.ZERO) > 0
            ? GstAdjustmentType.CREDIT_NOTE_REQUIRED
            : GstAdjustmentType.INVOICE_CANCELLATION;
        String gstAdjustmentRef = normalizeOptionalText(request.gstAdjustmentReference());
        if (gstAdjustmentRef == null) {
            gstAdjustmentRef = "CANC-" + sale.invoiceNumber();
        }

        SalesInvoiceCancellation cancellation = salesCancellationRepository.createCancellation(
            new SalesInvoiceCancellation(
                null,
                UUID.randomUUID(),
                sale.id(),
                sale.invoiceNumber(),
                idempotencyKey,
                reason,
                currentUserId,
                now,
                money(sale.grandTotal()),
                money(sale.taxableAmount()),
                money(sale.cgstAmount()),
                money(sale.sgstAmount()),
                money(sale.igstAmount()),
                money(sale.totalTaxAmount()),
                previouslyPaidAmount,
                previouslyReturnedAmount,
                previouslyRefundedAmount,
                cancelledInvoiceAmount,
                refundableAmount,
                primaryRefundMethod,
                overallRefundStatus,
                primaryRefundRef,
                confirmedAt,
                confirmedBy,
                receivableAdjustmentAmount,
                gstAdjustmentType,
                gstAdjustmentRef,
                null,
                null,
                "NOT_APPLICABLE",
                null,
                "NOT_APPLICABLE",
                normalizeOptionalText(request.notes()),
                null,
                null
            )
        );

        for (ItemCancellationPlan plan : itemPlans) {
            salesCancellationRepository.createCancellationItem(new SalesInvoiceCancellationItem(
                null,
                UUID.randomUUID(),
                cancellation.id(),
                plan.salesProduct().id(),
                plan.salesProduct().productId(),
                plan.salesProduct().productCode(),
                plan.salesProduct().productName(),
                plan.salesProduct().quantity(),
                plan.previouslyReturnedQuantity(),
                plan.remainingQuantityToRestore(),
                plan.salesProduct().unit(),
                plan.taxableReversed(),
                plan.cgstReversed(),
                plan.sgstReversed(),
                plan.igstReversed(),
                plan.totalTaxReversed(),
                plan.lineTotalReversed(),
                null
            ));

            if (plan.remainingQuantityToRestore().compareTo(BigDecimal.ZERO) > 0) {
                inventoryService.restoreStockFromSalesCancellation(
                    plan.salesProduct().productId(),
                    plan.remainingQuantityToRestore(),
                    cancellation.id(),
                    sale.invoiceNumber()
                );
            }
        }

        for (RefundPlan plan : refundPlans) {
            UUID refundUuid = UUID.randomUUID();
            SalesCancellationRefund refund = salesCancellationRepository.createCancellationRefund(
                new SalesCancellationRefund(
                    null,
                    refundUuid,
                    cancellation.id(),
                    sale.id(),
                    plan.originalPaymentId(),
                    plan.paymentMethod(),
                    plan.amount(),
                    plan.status(),
                    plan.referenceNumber(),
                    now,
                    plan.status() == CancellationRefundStatus.SUCCESSFUL ? now : null,
                    plan.status() == CancellationRefundStatus.SUCCESSFUL ? currentUserId : null,
                    plan.notes(),
                    null,
                    null
                )
            );

            if (refund.paymentMethod() == PaymentMethod.CASH) {
                cashRegisterService.recordTransactionMovement(
                    CashMovementType.OUT,
                    CashMovementSourceType.SALES_CANCELLATION_REFUND,
                    refund.uuid(),
                    refund.amount(),
                    "Invoice cancellation refund for " + sale.invoiceNumber()
                );
            }
        }

        Sales cancelledSale = new Sales(
            sale.id(),
            sale.uuid(),
            sale.invoiceNumber(),
            sale.customerId(),
            sale.customerName(),
            sale.customerPhone(),
            sale.customerGstin(),
            sale.supplyType(),
            sale.placeOfSupply(),
            sale.billingAddress(),
            sale.shippingAddress(),
            sale.customerReference(),
            sale.paymentTerms(),
            sale.dueDate(),
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
            BigDecimal.ZERO.setScale(MONEY_SCALE),
            SalesStatus.CANCELLED,
            SalesPaymentStatus.UNPAID,
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(cancelledSale);
        salesRepository.saveCancellationReason(sale.id(), reason);

        auditLogService.log(
            "CANCEL_ISSUED",
            "SALES",
            sale.invoiceNumber(),
            "Issued sales invoice cancelled: " + reason,
            Map.of(
                "status", sale.status().name(),
                "paidAmount", previouslyPaidAmount,
                "balanceAmount", sale.balanceAmount()
            ),
            Map.of(
                "status", SalesStatus.CANCELLED.name(),
                "cancellationUuid", cancellation.uuid().toString(),
                "refundAmount", refundableAmount,
                "refundStatus", overallRefundStatus.name(),
                "receivableAdjustmentAmount", receivableAdjustmentAmount,
                "gstAdjustmentType", gstAdjustmentType.name()
            )
        );

        logger.info("Issued sales invoice {} cancelled successfully. Refund: {}, Receivable adj: {}",
            sale.invoiceNumber(), refundableAmount, receivableAdjustmentAmount);

        return findByUuid(sale.uuid());
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("SALES.CANCEL_ISSUED")
    public SalesCancellationPreviewResponse getCancellationPreview(UUID saleUuid) {
        Sales sale = salesRepository.findByUuid(saleUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));

        List<SalesProduct> saleProducts = salesRepository.findProductsBySaleId(sale.id());
        List<SalesPayment> payments = salesRepository.findPaymentsBySaleId(sale.id());
        List<SalesReturn> previousReturns = salesReturnRepository != null
            ? salesReturnRepository.findReturnsBySaleId(sale.id())
            : List.of();

        BigDecimal previouslyPaidAmount = money(sale.paidAmount());
        BigDecimal previouslyReturnedAmount = money(previousReturns.stream()
            .map(SalesReturn::totalReturnAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal previouslyRefundedAmount = salesReturnRepository != null
            ? money(salesReturnRepository.getTotalRefundedAmountBySaleId(sale.id()))
            : BigDecimal.ZERO.setScale(MONEY_SCALE);

        BigDecimal cancelledInvoiceAmount = money(sale.grandTotal().subtract(previouslyReturnedAmount).max(BigDecimal.ZERO));
        BigDecimal refundableAmount = money(previouslyPaidAmount.subtract(previouslyRefundedAmount).max(BigDecimal.ZERO));
        BigDecimal receivableAdjustmentAmount = money(cancelledInvoiceAmount.subtract(refundableAmount).max(BigDecimal.ZERO));

        List<ItemCancellationPlan> itemPlans = buildItemCancellationPlans(saleProducts);

        BigDecimal taxableReversed = money(itemPlans.stream().map(ItemCancellationPlan::taxableReversed).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal cgstReversed = money(itemPlans.stream().map(ItemCancellationPlan::cgstReversed).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal sgstReversed = money(itemPlans.stream().map(ItemCancellationPlan::sgstReversed).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal igstReversed = money(itemPlans.stream().map(ItemCancellationPlan::igstReversed).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal totalTaxReversed = money(itemPlans.stream().map(ItemCancellationPlan::totalTaxReversed).reduce(BigDecimal.ZERO, BigDecimal::add));

        boolean dailyPeriodClosed = dailyClosingRepository != null
            && dailyClosingRepository.findByBusinessDate(sale.saleDate()).isPresent();

        boolean eligible = sale.status() == SalesStatus.COMPLETED
            && !(blockClosedDailyPeriod && dailyPeriodClosed);
        String ineligibilityReason = null;
        if (sale.status() == SalesStatus.CANCELLED) {
            ineligibilityReason = "Invoice is already cancelled";
        } else if (sale.status() == SalesStatus.DRAFT) {
            ineligibilityReason = "Draft invoices should use draft cancellation";
        } else if (blockClosedDailyPeriod && dailyPeriodClosed) {
            ineligibilityReason = "Daily closing has already been completed for " + sale.saleDate();
        }

        List<String> warnings = new ArrayList<>();
        if (previouslyReturnedAmount.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("Partial sales returns exist; only remaining unreturned stock and net paid amounts will be reversed.");
        }
        if (dailyPeriodClosed && !blockClosedDailyPeriod) {
            warnings.add("Business date " + sale.saleDate() + " has a daily closing record; cancellation refund will post to the current open session.");
        }
        boolean hasCashPayment = payments.stream().anyMatch(p -> p.paymentMethod() == PaymentMethod.CASH);
        if (refundableAmount.compareTo(BigDecimal.ZERO) > 0 && hasCashPayment) {
            warnings.add("Cash refunds require an open cash register session for the current user.");
        }

        GstAdjustmentType gstAdjustmentType = previouslyReturnedAmount.compareTo(BigDecimal.ZERO) > 0
            ? GstAdjustmentType.CREDIT_NOTE_REQUIRED
            : GstAdjustmentType.INVOICE_CANCELLATION;

        List<SalesPaymentResponse> paymentResponses = payments.stream()
            .map(p -> new SalesPaymentResponse(
                p.uuid(), p.paymentMethod(), p.amount(), p.referenceNumber(), p.paymentDate(), p.notes()
            ))
            .toList();

        List<SalesCancellationPreviewResponse.PreviewItem> previewItems = itemPlans.stream()
            .map(plan -> new SalesCancellationPreviewResponse.PreviewItem(
                plan.salesProduct().uuid(),
                plan.salesProduct().productUuid(),
                plan.salesProduct().productCode(),
                plan.salesProduct().productName(),
                plan.salesProduct().unit(),
                plan.salesProduct().quantity(),
                plan.previouslyReturnedQuantity(),
                plan.remainingQuantityToRestore(),
                plan.taxableReversed(),
                plan.totalTaxReversed(),
                plan.lineTotalReversed()
            ))
            .toList();

        return new SalesCancellationPreviewResponse(
            sale.uuid(),
            sale.invoiceNumber(),
            sale.saleDate(),
            sale.customerName(),
            sale.customerPhone(),
            sale.status(),
            eligible,
            ineligibilityReason,
            money(sale.grandTotal()),
            previouslyPaidAmount,
            previouslyReturnedAmount,
            previouslyRefundedAmount,
            money(sale.balanceAmount()),
            cancelledInvoiceAmount,
            refundableAmount,
            receivableAdjustmentAmount,
            gstAdjustmentType,
            taxableReversed,
            cgstReversed,
            sgstReversed,
            igstReversed,
            totalTaxReversed,
            refundableAmount.compareTo(BigDecimal.ZERO) > 0 && hasCashPayment,
            dailyPeriodClosed,
            paymentResponses,
            previewItems,
            warnings
        );
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("SALES.VIEW")
    public SalesCancellationResponse getCancellation(UUID saleUuid) {
        Sales sale = salesRepository.findByUuid(saleUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));

        if (salesCancellationRepository == null) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_NOT_FOUND);
        }

        return salesCancellationRepository.findDetailedBySaleId(sale.id())
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_CANCELLATION_NOT_FOUND));
    }

    @Override
    @AdminOrManager
    @Transactional
    @RequiresPermission("SALES.CANCEL_ISSUED")
    public SalesCancellationResponse confirmCancellationRefund(
        UUID saleUuid,
        UUID refundUuid,
        SalesCancellationRefundConfirmRequest request
    ) {
        if (salesCancellationRepository == null || currentUserService == null) {
            throw new IllegalStateException("Cancellation repository is not configured");
        }

        Sales sale = salesRepository.findByUuidForUpdate(saleUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_NOT_FOUND));

        SalesInvoiceCancellation cancellation = salesCancellationRepository.findBySaleId(sale.id())
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_CANCELLATION_NOT_FOUND));

        SalesCancellationRefund refund = salesCancellationRepository.findRefundByUuidForUpdate(refundUuid)
            .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_NOT_FOUND));

        if (!refund.cancellationId().equals(cancellation.id())) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_NOT_FOUND);
        }

        if (refund.status() == CancellationRefundStatus.SUCCESSFUL && request.status() == CancellationRefundStatus.SUCCESSFUL) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_INVALID);
        }

        if (request.status() == CancellationRefundStatus.NOT_REQUIRED) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_INVALID);
        }

        Long currentUserId = currentUserService.getCurrentUserId();
        OffsetDateTime now = OffsetDateTime.now();
        String ref = normalizeOptionalText(request.referenceNumber());
        if (ref == null) {
            ref = refund.referenceNumber();
        }

        SalesCancellationRefund updatedRefund = salesCancellationRepository.updateCancellationRefund(
            new SalesCancellationRefund(
                refund.id(),
                refund.uuid(),
                refund.cancellationId(),
                refund.saleId(),
                refund.originalPaymentId(),
                refund.paymentMethod(),
                refund.amount(),
                request.status(),
                ref,
                refund.refundDate(),
                request.status() == CancellationRefundStatus.SUCCESSFUL ? now : refund.confirmedAt(),
                request.status() == CancellationRefundStatus.SUCCESSFUL ? currentUserId : refund.confirmedBy(),
                normalizeOptionalText(request.notes()) != null ? normalizeOptionalText(request.notes()) : refund.notes(),
                refund.createdAt(),
                now
            )
        );

        List<SalesCancellationRefund> allRefunds = salesCancellationRepository.findRefundsByCancellationId(cancellation.id());
        CancellationRefundStatus overallStatus = aggregateRefundStatus(allRefunds);
        salesCancellationRepository.updateCancellation(
            new SalesInvoiceCancellation(
                cancellation.id(),
                cancellation.uuid(),
                cancellation.saleId(),
                cancellation.invoiceNumber(),
                cancellation.idempotencyKey(),
                cancellation.cancellationReason(),
                cancellation.cancelledBy(),
                cancellation.cancelledAt(),
                cancellation.originalGrandTotal(),
                cancellation.originalTaxableAmount(),
                cancellation.originalCgstAmount(),
                cancellation.originalSgstAmount(),
                cancellation.originalIgstAmount(),
                cancellation.originalTotalTaxAmount(),
                cancellation.previouslyPaidAmount(),
                cancellation.previouslyReturnedAmount(),
                cancellation.previouslyRefundedAmount(),
                cancellation.cancelledInvoiceAmount(),
                cancellation.refundAmount(),
                cancellation.refundMethod(),
                overallStatus,
                ref,
                overallStatus == CancellationRefundStatus.SUCCESSFUL ? now : null,
                overallStatus == CancellationRefundStatus.SUCCESSFUL ? currentUserId : null,
                cancellation.receivableAdjustmentAmount(),
                cancellation.gstAdjustmentType(),
                cancellation.gstAdjustmentReference(),
                cancellation.creditNoteNumber(),
                cancellation.irn(),
                cancellation.irnStatus(),
                cancellation.ewayBillNumber(),
                cancellation.ewayBillStatus(),
                cancellation.notes(),
                cancellation.createdAt(),
                now
            )
        );

        auditLogService.log(
            "UPDATE",
            "SALES_CANCELLATION_REFUND",
            updatedRefund.uuid().toString(),
            "Cancellation refund status updated to " + request.status().name(),
            Map.of("status", refund.status().name()),
            Map.of("status", updatedRefund.status().name(), "referenceNumber", ref != null ? ref : "")
        );

        return getCancellation(saleUuid);
    }

    private void enforceCancelIssuedPermission() {
        if (permissionService == null) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !permissionService.hasPermission(auth, "SALES.CANCEL_ISSUED")) {
            throw new AccessDeniedException("You do not have permission to cancel an issued sales invoice.");
        }
    }

    private void validateFinancialConsistency(Sales sale, List<SalesPayment> payments) {
        BigDecimal sumPayments = money(payments.stream()
            .map(SalesPayment::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (sumPayments.subtract(money(sale.paidAmount())).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_CONFLICT);
        }
        BigDecimal expectedBalance = money(sale.grandTotal().subtract(sale.paidAmount()));
        if (expectedBalance.subtract(money(sale.balanceAmount())).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new ApiException(SalesErrorCode.SALES_CANCELLATION_CONFLICT);
        }
    }

    private List<ItemCancellationPlan> buildItemCancellationPlans(List<SalesProduct> saleProducts) {
        List<SalesProduct> sorted = saleProducts.stream()
            .sorted(Comparator.comparing(SalesProduct::productId))
            .toList();

        List<ItemCancellationPlan> plans = new ArrayList<>();
        for (SalesProduct sp : sorted) {
            List<SalesReturnProduct> prevReturns = salesReturnRepository != null
                ? salesReturnRepository.findReturnedProductsBySaleProductId(sp.id())
                : List.of();

            BigDecimal prevQty = prevReturns.stream()
                .map(SalesReturnProduct::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainingQty = sp.quantity().subtract(prevQty);

            if (remainingQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(SalesErrorCode.SALES_CANCELLATION_CONFLICT);
            }

            BigDecimal prevTaxable = prevReturns.stream().map(SalesReturnProduct::taxableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prevCgst = prevReturns.stream().map(SalesReturnProduct::cgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prevSgst = prevReturns.stream().map(SalesReturnProduct::sgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prevIgst = prevReturns.stream().map(SalesReturnProduct::igstAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prevTotalTax = prevReturns.stream().map(SalesReturnProduct::totalTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prevLineTotal = prevReturns.stream().map(SalesReturnProduct::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

            plans.add(new ItemCancellationPlan(
                sp,
                prevQty,
                remainingQty,
                money(sp.taxableAmount().subtract(prevTaxable).max(BigDecimal.ZERO)),
                money(sp.cgstAmount().subtract(prevCgst).max(BigDecimal.ZERO)),
                money(sp.sgstAmount().subtract(prevSgst).max(BigDecimal.ZERO)),
                money(sp.igstAmount().subtract(prevIgst).max(BigDecimal.ZERO)),
                money(sp.totalTaxAmount().subtract(prevTotalTax).max(BigDecimal.ZERO)),
                money(sp.lineTotal().subtract(prevLineTotal).max(BigDecimal.ZERO))
            ));
        }
        return plans;
    }

    private List<RefundPlan> buildRefundPlans(
        Sales sale,
        List<SalesPayment> payments,
        BigDecimal refundableAmount,
        SalesCancelRequest request
    ) {
        if (refundableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        boolean confirmExternal = Boolean.TRUE.equals(request.confirmExternalRefund());
        String defaultRef = normalizeOptionalText(request.refundReference());

        if (request.refundAllocations() != null && !request.refundAllocations().isEmpty()) {
            BigDecimal allocatedTotal = money(request.refundAllocations().stream()
                .map(SalesCancellationRefundAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

            if (allocatedTotal.compareTo(refundableAmount) != 0) {
                throw new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_INVALID);
            }

            List<RefundPlan> plans = new ArrayList<>();
            for (SalesCancellationRefundAllocation alloc : request.refundAllocations()) {
                if (alloc.paymentMethod() == null || alloc.paymentMethod() == PaymentMethod.CREDIT) {
                    throw new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_INVALID);
                }
                Long originalPaymentId = null;
                if (alloc.originalPaymentUuid() != null) {
                    SalesPayment matched = payments.stream()
                        .filter(p -> p.uuid().equals(alloc.originalPaymentUuid()))
                        .findFirst()
                        .orElseThrow(() -> new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_INVALID));
                    originalPaymentId = matched.id();
                }
                String ref = normalizeOptionalText(alloc.referenceNumber());
                if (ref == null) {
                    ref = defaultRef;
                }
                CancellationRefundStatus status = resolveInitialRefundStatus(alloc.paymentMethod(), confirmExternal, ref);
                plans.add(new RefundPlan(originalPaymentId, alloc.paymentMethod(), money(alloc.amount()), status, ref, null));
            }
            return plans;
        }

        if (request.refundMethod() != null) {
            if (request.refundMethod() == PaymentMethod.CREDIT) {
                throw new ApiException(SalesErrorCode.SALES_CANCELLATION_REFUND_INVALID);
            }
            Long originalPaymentId = payments.size() == 1 ? payments.get(0).id() : null;
            CancellationRefundStatus status = resolveInitialRefundStatus(request.refundMethod(), confirmExternal, defaultRef);
            return List.of(new RefundPlan(originalPaymentId, request.refundMethod(), refundableAmount, status, defaultRef, null));
        }

        // Default: trace refunds back to original non-CREDIT payments up to refundableAmount.
        List<RefundPlan> autoPlans = new ArrayList<>();
        BigDecimal remaining = refundableAmount;
        for (SalesPayment payment : payments) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (payment.paymentMethod() == PaymentMethod.CREDIT) {
                continue;
            }
            BigDecimal chunk = money(payment.amount().min(remaining));
            if (chunk.compareTo(BigDecimal.ZERO) > 0) {
                String ref = defaultRef != null ? defaultRef : payment.referenceNumber();
                CancellationRefundStatus status = resolveInitialRefundStatus(payment.paymentMethod(), confirmExternal, ref);
                autoPlans.add(new RefundPlan(payment.id(), payment.paymentMethod(), chunk, status, ref, null));
                remaining = money(remaining.subtract(chunk));
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Fallback if original payment was CREDIT or unallocated: require explicit method or default to CASH.
            CancellationRefundStatus status = resolveInitialRefundStatus(PaymentMethod.CASH, confirmExternal, defaultRef);
            autoPlans.add(new RefundPlan(null, PaymentMethod.CASH, remaining, status, defaultRef, null));
        }

        return autoPlans;
    }

    private CancellationRefundStatus resolveInitialRefundStatus(
        PaymentMethod method,
        boolean confirmExternal,
        String reference
    ) {
        if (method == PaymentMethod.CASH) {
            return CancellationRefundStatus.SUCCESSFUL;
        }
        if (confirmExternal && reference != null && !reference.isBlank()) {
            return CancellationRefundStatus.SUCCESSFUL;
        }
        return CancellationRefundStatus.PENDING;
    }

    private CancellationRefundStatus determineOverallRefundStatus(
        BigDecimal refundableAmount,
        List<RefundPlan> plans
    ) {
        if (refundableAmount.compareTo(BigDecimal.ZERO) <= 0 || plans.isEmpty()) {
            return CancellationRefundStatus.NOT_REQUIRED;
        }
        boolean allSuccess = plans.stream().allMatch(p -> p.status() == CancellationRefundStatus.SUCCESSFUL);
        if (allSuccess) {
            return CancellationRefundStatus.SUCCESSFUL;
        }
        return CancellationRefundStatus.PENDING;
    }

    private CancellationRefundStatus aggregateRefundStatus(List<SalesCancellationRefund> refunds) {
        if (refunds.isEmpty()) {
            return CancellationRefundStatus.NOT_REQUIRED;
        }
        if (refunds.stream().allMatch(r -> r.status() == CancellationRefundStatus.SUCCESSFUL)) {
            return CancellationRefundStatus.SUCCESSFUL;
        }
        if (refunds.stream().anyMatch(r -> r.status() == CancellationRefundStatus.FAILED)) {
            return CancellationRefundStatus.FAILED;
        }
        if (refunds.stream().anyMatch(r -> r.status() == CancellationRefundStatus.PROCESSING)) {
            return CancellationRefundStatus.PROCESSING;
        }
        if (refunds.stream().allMatch(r -> r.status() == CancellationRefundStatus.REVERSED)) {
            return CancellationRefundStatus.REVERSED;
        }
        return CancellationRefundStatus.PENDING;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ItemCancellationPlan(
        SalesProduct salesProduct,
        BigDecimal previouslyReturnedQuantity,
        BigDecimal remainingQuantityToRestore,
        BigDecimal taxableReversed,
        BigDecimal cgstReversed,
        BigDecimal sgstReversed,
        BigDecimal igstReversed,
        BigDecimal totalTaxReversed,
        BigDecimal lineTotalReversed
    ) {}

    private record RefundPlan(
        Long originalPaymentId,
        PaymentMethod paymentMethod,
        BigDecimal amount,
        CancellationRefundStatus status,
        String referenceNumber,
        String notes
    ) {}

    // ---------------------------------------------------------
    // BUILD SALES RESPONSE
    // ---------------------------------------------------------

    private SalesResponse toResponse(
        Sales sale,
        UUID customerUuid,
        String cancellationReason,
        SalesReturnStatus returnStatus,
        BigDecimal returnedAmount,
        BigDecimal refundedAmount,
        BigDecimal outstandingRefundAmount,
        List<SalesProductResponse> products,
        List<SalesPaymentResponse> payments
    ) {
        return new SalesResponse(
            sale.uuid(),
            sale.invoiceNumber(),
            customerUuid,
            sale.customerName(),
            sale.customerPhone(),
            sale.customerGstin(),
            sale.supplyType(),
            sale.placeOfSupply(),
            sale.billingAddress(),
            sale.shippingAddress(),
            sale.customerReference(),
            sale.paymentTerms(),
            sale.dueDate(),
            sale.saleDate(),
            sale.subtotal(),
            sale.discountAmount(),
            sale.taxableAmount(),
            sale.cgstAmount(),
            sale.sgstAmount(),
            sale.igstAmount(),
            sale.totalTaxAmount(),
            sale.grandTotal(),
            money(sale.grandTotal().subtract(sale.taxableAmount()).subtract(sale.totalTaxAmount())),
            sale.paidAmount(),
            sale.status() == SalesStatus.CANCELLED ? BigDecimal.ZERO.setScale(MONEY_SCALE) : sale.balanceAmount(),
            sale.status(),
            sale.status() == SalesStatus.CANCELLED ? null : sale.paymentStatus(),
            returnStatus,
            returnedAmount,
            refundedAmount,
            outstandingRefundAmount,
            sale.notes(),
            cancellationReason,
            products,
            payments,
            sale.createdAt(),
            sale.updatedAt()
        );
    }

    private void validateInitialPayments(List<SalesPaymentRequest> payments) {
        if (payments == null || payments.isEmpty()) return;
        for (SalesPaymentRequest p : payments) {
            if (p.paymentMethod() == null || p.paymentMethod() == PaymentMethod.CREDIT) {
                throw new ApiException(SalesErrorCode.INVALID_SALES_PAYMENT);
            }
            if (p.amount() == null || p.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(SalesErrorCode.INVALID_SALES_PAYMENT);
            }
        }
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

    private void requireConfiguredSalesStatus(SalesStatus status) {
        if (!salesRepository.isSalesStatusConfigured(status.name())) {
            throw new IllegalStateException("Sales status is missing or inactive in sales_statuses: " + status.name());
        }
    }

    private void requireConfiguredPaymentStatus(SalesPaymentStatus status) {
        if (!salesRepository.isSalesPaymentStatusConfigured(status.name())) {
            throw new IllegalStateException("Payment status is missing or inactive in sales_payment_statuses: " + status.name());
        }
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
    @RequiresPermission("SALES.PAYMENT_RECORD")
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

        if (request.paymentMethod() == null || request.paymentMethod() == PaymentMethod.CREDIT) {
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
        requireConfiguredPaymentStatus(paymentStatus);

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
            sale.customerGstin(),
            sale.supplyType(),
            sale.placeOfSupply(),
            sale.billingAddress(),
            sale.shippingAddress(),
            sale.customerReference(),
            sale.paymentTerms(),
            sale.dueDate(),
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
