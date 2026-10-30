
package com.autoparts.erp.tax.model;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum TaxErrorCode implements ApiErrorCodeDefinition {

    TAX_RATE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Tax rate not found"
    ),

    TAX_RATE_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Tax rate configuration already exists"
    ),

    TAX_RATE_ALREADY_IN_USE(
        HttpStatus.CONFLICT,
        "Tax rate is already used and cannot be modified"
    ),

    INVALID_TAX_RATE(
        HttpStatus.BAD_REQUEST,
        "Invalid GST rate configuration"
    ),

    INVALID_TAX_EFFECTIVE_DATE(
        HttpStatus.BAD_REQUEST,
        "Invalid tax effective date"
    );

    private final HttpStatus status;
    private final String message;

    TaxErrorCode(HttpStatus status, String message) {
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
