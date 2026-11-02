package com.autoparts.erp.salesledger.exception;

import org.springframework.http.HttpStatus;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;

public enum SalesLedgerErrorCode implements ApiErrorCodeDefinition {

    INVALID_DATE_RANGE(
        HttpStatus.BAD_REQUEST,
        "From date cannot be after to date"
    ),

    INVALID_PAGINATION(
        HttpStatus.BAD_REQUEST,
        "Invalid sales ledger pagination values"
    );

    private final HttpStatus status;
    private final String message;

    SalesLedgerErrorCode(HttpStatus status, String message) {
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