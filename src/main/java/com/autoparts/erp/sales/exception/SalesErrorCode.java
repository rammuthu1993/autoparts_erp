package com.autoparts.erp.sales.exception;

import org.springframework.http.HttpStatus;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;

public enum SalesErrorCode implements ApiErrorCodeDefinition {

    SALES_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Sales invoice not found"
    ),

    PRODUCT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Product not found"
    ),

    PRODUCT_TAX_MAPPING_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Current product tax mapping not found"
    ),

    INSUFFICIENT_STOCK(
        HttpStatus.CONFLICT,
        "Insufficient product stock"
    ),

    INVALID_SALES_AMOUNT(
        HttpStatus.BAD_REQUEST,
        "Invalid sales amount"
    ),

    INVALID_SALES_PAYMENT(
        HttpStatus.BAD_REQUEST,
        "Invalid sales payment"
    ),

    SALES_ALREADY_COMPLETED(
        HttpStatus.CONFLICT,
        "Sales invoice has already been completed"
    ),

    SALES_ALREADY_CANCELLED(
        HttpStatus.CONFLICT,
        "Sales invoice has already been cancelled"
    ),

    SALES_PAYMENT_EXCEEDS_TOTAL(
        HttpStatus.BAD_REQUEST,
        "Payment amount cannot exceed invoice total"
    ),

    INVALID_SALES_DISCOUNT(
        HttpStatus.BAD_REQUEST,
        "Discount cannot exceed product amount"
    ),

    SALES_DUPLICATE_INVOICE(
        HttpStatus.CONFLICT,
        "Sales invoice number already exists"
    );

    private final HttpStatus status;
    private final String message;

    SalesErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}