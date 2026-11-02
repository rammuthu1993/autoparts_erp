package com.autoparts.erp.sales.exception;

import org.springframework.http.HttpStatus;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;

public enum SalesErrorCode implements ApiErrorCodeDefinition {

	SALES_NOT_FOUND(HttpStatus.NOT_FOUND, "Sales invoice not found"),

	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),

	PRODUCT_TAX_MAPPING_NOT_FOUND(HttpStatus.NOT_FOUND, "Current product tax mapping not found"),

	INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient product stock"),

	INVALID_SALES_AMOUNT(HttpStatus.BAD_REQUEST, "Invalid sales amount"),

	INVALID_SALES_PAYMENT(HttpStatus.BAD_REQUEST, "Invalid sales payment"),

	SALES_ALREADY_COMPLETED(HttpStatus.CONFLICT, "Sales invoice has already been completed"),

	SALES_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Sales invoice has already been cancelled"),

	SALES_PAYMENT_EXCEEDS_TOTAL(HttpStatus.BAD_REQUEST, "Payment amount cannot exceed invoice total"),

	INVALID_SALES_DISCOUNT(HttpStatus.BAD_REQUEST, "Discount cannot exceed product amount"),

	SALES_DUPLICATE_INVOICE(HttpStatus.CONFLICT, "Sales invoice number already exists"),
	SALES_PAYMENT_NOT_ALLOWED(HttpStatus.CONFLICT, "Payments can only be recorded for completed sales invoices"),
	SALES_PAYMENT_EXCEEDS_BALANCE(HttpStatus.BAD_REQUEST, "Payment amount cannot exceed the outstanding balance"),
	SALES_RETURN_NOT_ALLOWED(HttpStatus.CONFLICT, "Returns can only be created for completed sales invoices"),

	SALES_RETURN_NOT_FOUND(HttpStatus.NOT_FOUND, "Sales return not found"),

	SALES_RETURN_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Sales invoice product not found"),

	INVALID_SALES_RETURN(HttpStatus.BAD_REQUEST, "Invalid sales return"),

	SALES_RETURN_EXCEEDS_SOLD_QUANTITY(HttpStatus.CONFLICT,
			"Return quantity cannot exceed the remaining sold quantity"),

	SALES_REFUND_EXCEEDS_RETURN_AMOUNT(HttpStatus.BAD_REQUEST,
			"Refund amount cannot exceed the remaining refundable amount"),

	SALES_REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "Refund is not allowed for this sales return");
	

	private final HttpStatus status;
	private final String message;

	SalesErrorCode(HttpStatus status, String message) {
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