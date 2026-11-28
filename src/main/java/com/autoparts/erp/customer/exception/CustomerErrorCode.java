package com.autoparts.erp.customer.exception;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum CustomerErrorCode implements ApiErrorCodeDefinition {

    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Customer not found"
    ),

    CUSTOMER_ALREADY_INACTIVE(
            HttpStatus.CONFLICT,
            "Customer is already inactive"
    ),

    CUSTOMER_ALREADY_ACTIVE(
            HttpStatus.CONFLICT,
            "Customer is already active"
    ),

    CUSTOMER_GSTIN_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "A customer with this GSTIN already exists"
    ),

    INVALID_CUSTOMER_DATA(
            HttpStatus.BAD_REQUEST,
            "Invalid customer data"
    ),

    CUSTOMER_LEDGER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Customer ledger not found"
    ),

    INVALID_CUSTOMER_LEDGER_FILTER(
            HttpStatus.BAD_REQUEST,
            "Invalid customer ledger filter"
    );

    private final HttpStatus status;
    private final String message;

    CustomerErrorCode(HttpStatus status, String message) {
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
