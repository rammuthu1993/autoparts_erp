package com.autoparts.erp.inventory.exception;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum InventoryErrorCode implements ApiErrorCodeDefinition {

    INVENTORY_PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Product not found or inactive"
    ),

    INVALID_STOCK_ADJUSTMENT(
            HttpStatus.BAD_REQUEST,
            "Stock adjustment quantity must not be zero"
    ),

    INVALID_STOCK_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "Stock quantity must be greater than zero"
    ),

    INSUFFICIENT_STOCK(
            HttpStatus.BAD_REQUEST,
            "Stock adjustment would result in negative inventory"
    ),

    INVALID_INVENTORY_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "From date cannot be after to date"
    ),

    INVALID_INVENTORY_PAGINATION(
            HttpStatus.BAD_REQUEST,
            "Invalid inventory pagination parameters"
    );

    private final HttpStatus status;
    private final String message;

    InventoryErrorCode(HttpStatus status, String message) {
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