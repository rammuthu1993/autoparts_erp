package com.autoparts.erp.sales.model;

import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.enums.SupplyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Sales(
    Long id,
    UUID uuid,
    String invoiceNumber,
    Long customerId,
    String customerName,
    String customerPhone,
    String customerGstin,
    SupplyType supplyType,
    String placeOfSupply,
    String billingAddress,
    String shippingAddress,
    String customerReference,
    String paymentTerms,
    LocalDate dueDate,
    LocalDate saleDate,
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal taxableAmount,
    BigDecimal cgstAmount,
    BigDecimal sgstAmount,
    BigDecimal igstAmount,
    BigDecimal totalTaxAmount,
    BigDecimal grandTotal,
    BigDecimal paidAmount,
    BigDecimal balanceAmount,
    SalesStatus status,
    SalesPaymentStatus paymentStatus,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public Sales(
        Long id,
        UUID uuid,
        String invoiceNumber,
        Long customerId,
        String customerName,
        String customerPhone,
        LocalDate saleDate,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal totalTaxAmount,
        BigDecimal grandTotal,
        BigDecimal paidAmount,
        BigDecimal balanceAmount,
        SalesStatus status,
        SalesPaymentStatus paymentStatus,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        this(
            id, uuid, invoiceNumber, customerId, customerName, customerPhone,
            null, SupplyType.INTRA_STATE, null, null, null, null, null, null,
            saleDate, subtotal, discountAmount, taxableAmount, cgstAmount, sgstAmount, igstAmount,
            totalTaxAmount, grandTotal, paidAmount, balanceAmount, status, paymentStatus, notes,
            createdAt, updatedAt
        );
    }
}
