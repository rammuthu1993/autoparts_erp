package com.autoparts.erp.customer.exception;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum CustomerErrorCode implements ApiErrorCodeDefinition {

    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Customer not found"
    ),

    CUSTOMER_ALREADY_INACTIVE(
            HttpStatus.BAD_REQUEST,
            "Customer is already inactive"
    ),CUSTOMER_LEDGER_NOT_FOUND(
    		HttpStatus.NOT_FOUND,
            "Customer ledger found"),
    INVALID_CUSTOMER_LEDGER_FILTER(
    		HttpStatus.NOT_FOUND,
            "Invalid customer ledger");

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