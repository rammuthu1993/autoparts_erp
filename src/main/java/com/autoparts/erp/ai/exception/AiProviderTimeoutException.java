
package com.autoparts.erp.ai.exception;

public class AiProviderTimeoutException extends RuntimeException {

    public AiProviderTimeoutException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
