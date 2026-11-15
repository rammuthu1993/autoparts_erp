package com.autoparts.erp.ai.exception;

public class AiAgentExecutionTimeoutException extends RuntimeException {

    public AiAgentExecutionTimeoutException(String message) {
        super(message);
    }

    public AiAgentExecutionTimeoutException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}