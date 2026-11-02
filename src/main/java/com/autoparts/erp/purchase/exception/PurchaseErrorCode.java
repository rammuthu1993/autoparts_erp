
package com.autoparts.erp.purchase.exception;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum PurchaseErrorCode implements ApiErrorCodeDefinition {

    PURCHASE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Purchase not found"
    ),

    PURCHASE_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Purchase invoice already exists for this supplier"
    ),

    PURCHASE_PRODUCT_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "Product already exists in this purchase"
    ),

    PURCHASE_ALREADY_RECEIVED(
        HttpStatus.CONFLICT,
        "Purchase has already been received"
    ),

    PURCHASE_NOT_DRAFT(
        HttpStatus.CONFLICT,
        "Only draft purchases can be modified or received"
    ),

    SUPPLIER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Supplier not found or inactive"
    ),

    PRODUCT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "Product not found or inactive"
    ),

    INVALID_PURCHASE_DATA(
        HttpStatus.BAD_REQUEST,
        "Invalid purchase data"
    ),

    INVALID_PURCHASE_STATUS(
        HttpStatus.CONFLICT,
        "Invalid purchase status transition"
    ),
	

	PURCHASE_PRODUCTS_NOT_FOUND(
	    HttpStatus.CONFLICT,
	    "Purchase has no products"
	),
	
	PURCHASE_RECEIPT_FAILED(
	    HttpStatus.CONFLICT,
	    "Purchase could not be received"
	),
	INVALID_PURCHASE_LEDGER_FILTER(
            HttpStatus.BAD_REQUEST,
            "Invalid purchase ledger filter"
    ),
	PURCHASE_PAYMENT_NOT_FOUND(
		    HttpStatus.NOT_FOUND,
		    "Purchase payment not found"
		),

	PURCHASE_NOT_RECEIVED_FOR_PAYMENT(
		    HttpStatus.CONFLICT,
		    "Payments can only be recorded for received purchases"
		),

	PURCHASE_PAYMENT_EXCEEDS_BALANCE(
		    HttpStatus.BAD_REQUEST,
		    "Payment amount cannot exceed the outstanding purchase balance"
		),

	INVALID_PURCHASE_PAYMENT(
		    HttpStatus.BAD_REQUEST,
		    "Invalid purchase payment"
		);


    private final HttpStatus status;
    private final String message;

    PurchaseErrorCode(HttpStatus status, String message) {
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
