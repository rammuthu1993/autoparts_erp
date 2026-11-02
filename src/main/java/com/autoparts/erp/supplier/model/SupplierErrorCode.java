
package com.autoparts.erp.supplier.model;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum SupplierErrorCode implements ApiErrorCodeDefinition {

    SUPPLIER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Supplier not found"
    ),

    SUPPLIER_NAME_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Supplier name already exists"
    ),

    SUPPLIER_PHONE_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Supplier phone number already exists"
    ),

    SUPPLIER_EMAIL_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Supplier email already exists"
    ),

    SUPPLIER_GSTIN_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Supplier GSTIN already exists"
    ),

    INVALID_SUPPLIER_DATA(
        HttpStatus.BAD_REQUEST,
        "Invalid supplier data"
    ),SUPPLIER_LEDGER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Supplier not found"
    ),
    INVALID_SUPPLIER_LEDGER_FILTER(
            HttpStatus.BAD_REQUEST,
            "Invalid supplier ledger filter"
    );

    private final HttpStatus status;
    private final String message;

    SupplierErrorCode(HttpStatus status, String message) {
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
