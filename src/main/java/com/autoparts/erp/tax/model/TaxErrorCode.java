
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

    TAX_RATE_OVERLAPPING_PERIOD(
    	  HttpStatus.CONFLICT,
    	 "Another tax configuration already exists for this effective period"
    ),

    TAX_RATE_DUPLICATE_CONSTRAINT(
    	  HttpStatus.CONFLICT,
    	   "Tax rate violates a unique database constraint"
    ),
    

	PRODUCT_NOT_FOUND(
	    HttpStatus.NOT_FOUND,
	    "Product not found or inactive"
	),
	
	PRODUCT_TAX_MAPPING_NOT_FOUND(
	    HttpStatus.NOT_FOUND,
	    "Product tax mapping not found"
	),
	
	PRODUCT_TAX_MAPPING_ALREADY_EXISTS(
	    HttpStatus.CONFLICT,
	    "Product already has a tax mapping for this effective period"
	),
	
	TAX_RATE_NOT_VALID_FOR_DATE(
	    HttpStatus.BAD_REQUEST,
	    "Tax rate is not valid for the selected effective period"
	),
	
	PRODUCT_TAX_MAPPING_INVALID_DATE(
	    HttpStatus.BAD_REQUEST,
	    "Invalid product tax mapping effective dates"
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
