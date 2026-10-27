package com.autoparts.erp.product.model;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ApiErrorCodeDefinition {

    PRODUCT_NAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Product name already exists"
    ),

    PRODUCT_PART_NO_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Product part number already exists"
    ),

    PRODUCT_ITEM_ID_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Product item ID already exists"
    ),

    PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Product not found"
    ),

    CATEGORY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Product category not found or inactive"
    ),

    BRAND_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Product brand not found or inactive"
    ),

    INVALID_PRODUCT_DATA(
            HttpStatus.BAD_REQUEST,
            "Invalid product data"
    );

    private final HttpStatus status;
    private final String message;

    ProductErrorCode(HttpStatus status, String message) {
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