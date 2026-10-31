package com.autoparts.erp.sales.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesProductRequest;
import com.autoparts.erp.sales.dto.SalesProductResponse;
import com.autoparts.erp.sales.dto.SalesPaymentResponse;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.exception.SalesErrorCode;
import com.autoparts.erp.sales.model.Sale;
import com.autoparts.erp.sales.model.SaleProduct;
import com.autoparts.erp.sales.model.SalePayment;
import com.autoparts.erp.sales.repository.ProductStock;
import com.autoparts.erp.sales.repository.SalesRepository;
import com.autoparts.erp.sales.service.SalesService;
import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.autoparts.erp.tax.repository.TaxRateRepository;

@Service
public class SalesServiceImpl implements SalesService {

    private static final int MONEY_SCALE = 2;

    private final SalesRepository salesRepository;
    private final ProductTaxMappingRepository productTaxMappingRepository;
    private final TaxRateRepository taxRateRepository;

    public SalesServiceImpl(
        SalesRepository salesRepository,
        ProductTaxMappingRepository productTaxMappingRepository,
        TaxRateRepository taxRateRepository
    ) {
        this.salesRepository = salesRepository;
        this.productTaxMappingRepository = productTaxMappingRepository;
        this.taxRateRepository = taxRateRepository;
    }

    @Override
    @Transactional
    public SalesResponse create(SalesCreateRequest request) {

        validateRequest(request);

        UUID saleUuid = UUID.randomUUID();

        String invoiceNumber =
            salesRepository.generateInvoiceNumber(request.saleDate());

        if (salesRepository.existsInvoiceNumber(invoiceNumber)) {
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

        List<SaleProduct> saleItems = new ArrayList<>();

        for (SalesProductRequest itemRequest : request.products()) {

            Long productId = salesRepository
                .findActiveProductId(itemRequest.productUuid())
                .orElseThrow(() ->
                    new ApiException(SalesErrorCode.PRODUCT_NOT_FOUND)
                );

            ProductStock product = salesRepository
                .findProductStockForUpdate(productId)
                .orElseThrow(() ->
                    new ApiException(SalesErrorCode.PRODUCT_NOT_FOUND)
                );

            BigDecimal quantity = itemRequest.quantity();
            BigDecimal unitPrice = money(itemRequest.unitPrice());

            BigDecimal grossAmount = money(
                quantity.multiply(unitPrice)
            );

            BigDecimal discount = money(
                itemRequest.discountAmount() == null
                    ? BigDecimal.ZERO
                    : itemRequest.discountAmount()
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
                    .findCurrentByProductUuid(itemRequest.productUuid())
                    .orElseThrow(() ->
                        new ApiException(
                            SalesErrorCode.PRODUCT_TAX_MAPPING_NOT_FOUND
                        )
                    );

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

            SaleProduct saleProduct = new SaleProduct(
                null,
                UUID.randomUUID(),
                null,
                productId,
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

            saleItems.add(saleProduct);

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
            throw new ApiException(
                SalesErrorCode.SALES_PAYMENT_EXCEEDS_TOTAL
            );
        }

        BigDecimal balanceAmount = money(
            grandTotal.subtract(paidAmount)
        );

        SalesPaymentStatus paymentStatus =
            calculatePaymentStatus(paidAmount, grandTotal);

        Sale sale = new Sale(
            null,
            saleUuid,
            invoiceNumber,
            request.customerName(),
            request.customerPhone(),
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

        Sale createdSale = salesRepository.createSale(sale);

        for (SaleProduct item : saleItems) {

            SaleProduct savedItem = new SaleProduct(
                null,
                item.uuid(),
                createdSale.id(),
                item.productId(),
                item.productCode(),
                item.productName(),
                item.quantity(),
                item.unit(),
                item.unitPrice(),
                item.discountAmount(),
                item.taxableAmount(),
                item.hsnCode(),
                item.gstRate(),
                item.cgstRate(),
                item.sgstRate(),
                item.igstRate(),
                item.cgstAmount(),
                item.sgstAmount(),
                item.igstAmount(),
                item.totalTaxAmount(),
                item.lineTotal(),
                null
            );

            salesRepository.createSaleItem(savedItem);
        }

        if (request.payments() != null) {

            for (var paymentRequest : request.payments()) {

                SalePayment payment = new SalePayment(
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

        return findByUuid(createdSale.uuid());
    }

    @Override
    @Transactional
    public SalesResponse complete(UUID saleUuid) {

        Sale sale = salesRepository.findByUuidForUpdate(saleUuid)
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

        List<SaleProduct> items =
            salesRepository.findProductsBySaleId(sale.id());

        List<SaleProduct> sortedItems = items.stream()
            .sorted(Comparator.comparing(SaleProduct::productId))
            .toList();

        for (SaleProduct item : sortedItems) {

            ProductStock stock = salesRepository
                .findProductStockForUpdate(item.productId())
                .orElseThrow(() ->
                    new ApiException(SalesErrorCode.PRODUCT_NOT_FOUND)
                );

            if (stock.quantity().compareTo(item.quantity()) < 0) {
                throw new ApiException(
                    SalesErrorCode.INSUFFICIENT_STOCK
                );
            }

            BigDecimal afterQuantity = money(
                stock.quantity().subtract(item.quantity())
            );

            salesRepository.updateProductStock(
                item.productId(),
                afterQuantity
            );

            salesRepository.createInventoryMovement(
                item.productId(),
                item.quantity(),
                stock.quantity(),
                afterQuantity,
                sale.uuid()
            );
        }

        Sale completedSale = new Sale(
            sale.id(),
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
            SalesStatus.COMPLETED,
            sale.paymentStatus(),
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(completedSale);

        return findByUuid(saleUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesResponse findByUuid(UUID saleUuid) {

        Sale sale = salesRepository.findByUuid(saleUuid)
            .orElseThrow(() ->
                new ApiException(SalesErrorCode.SALES_NOT_FOUND)
            );

        List<SalesProductResponse> itemResponses =
            salesRepository.findProductsBySaleId(sale.id())
                .stream()
                .map(item -> new SalesProductResponse(
                    item.uuid(),
                    null,
                    item.productCode(),
                    item.productName(),
                    item.quantity(),
                    item.unit(),
                    item.unitPrice(),
                    item.discountAmount(),
                    item.taxableAmount(),
                    item.hsnCode(),
                    item.gstRate(),
                    item.cgstRate(),
                    item.sgstRate(),
                    item.igstRate(),
                    item.cgstAmount(),
                    item.sgstAmount(),
                    item.igstAmount(),
                    item.totalTaxAmount(),
                    item.lineTotal()
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

        return toResponse(sale, itemResponses, paymentResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesResponse> findAll() {

        return salesRepository.findAll()
            .stream()
            .map(sale -> toResponse(
                sale,
                salesRepository.findProductsBySaleId(sale.id())
                    .stream()
                    .map(item -> new SalesProductResponse(
                        item.uuid(),
                        null,
                        item.productCode(),
                        item.productName(),
                        item.quantity(),
                        item.unit(),
                        item.unitPrice(),
                        item.discountAmount(),
                        item.taxableAmount(),
                        item.hsnCode(),
                        item.gstRate(),
                        item.cgstRate(),
                        item.sgstRate(),
                        item.igstRate(),
                        item.cgstAmount(),
                        item.sgstAmount(),
                        item.igstAmount(),
                        item.totalTaxAmount(),
                        item.lineTotal()
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

    @Override
    @Transactional
    public SalesResponse cancel(UUID saleUuid) {

        Sale sale = salesRepository.findByUuidForUpdate(saleUuid)
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

        Sale cancelledSale = new Sale(
            sale.id(),
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
            SalesStatus.CANCELLED,
            sale.paymentStatus(),
            sale.notes(),
            sale.createdAt(),
            sale.updatedAt()
        );

        salesRepository.updateSale(cancelledSale);

        return findByUuid(saleUuid);
    }

    private SalesResponse toResponse(
        Sale sale,
        List<SalesProductResponse> items,
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
            items,
            payments,
            sale.createdAt(),
            sale.updatedAt()
        );
    }

    private void validateRequest(SalesCreateRequest request) {

        if (request.products() == null || request.products().isEmpty()) {
            throw new ApiException(
                SalesErrorCode.INVALID_SALES_AMOUNT
            );
        }
    }

    private BigDecimal calculatePaymentTotal(
        List<com.autoparts.erp.sales.dto.SalesPaymentRequest> payments
    ) {

        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        BigDecimal total = payments.stream()
            .map(payment -> money(payment.amount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return money(total);
    }

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

    private BigDecimal calculateTax(
        BigDecimal taxableAmount,
        BigDecimal taxRate
    ) {

        return money(
            taxableAmount
                .multiply(taxRate)
                .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal money(BigDecimal amount) {

        return amount.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
        );
    }
}