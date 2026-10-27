
package com.autoparts.erp.common.exception;

public class ApiException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final ApiErrorCodeDefinition error;

    public ApiException(ApiErrorCodeDefinition error) {
        super(error.getMessage());
        this.error = error;
    }

    public ApiErrorCodeDefinition getError() {
        return error;
    }
}
