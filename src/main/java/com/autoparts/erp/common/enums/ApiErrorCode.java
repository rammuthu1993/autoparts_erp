
package com.autoparts.erp.common.enums;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum ApiErrorCode implements ApiErrorCodeDefinition {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Authentication required"
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "Access denied"
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
    );

    private final HttpStatus status;
    private final String message;

    ApiErrorCode(HttpStatus status, String message) {
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
