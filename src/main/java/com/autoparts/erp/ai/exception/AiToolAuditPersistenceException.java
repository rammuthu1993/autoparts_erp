package com.autoparts.erp.ai.exception;

/** Raised when a required AI tool audit record cannot be persisted. */
public class AiToolAuditPersistenceException extends RuntimeException {

    public AiToolAuditPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiToolAuditPersistenceException(String message) {
        super(message);
    }
}
