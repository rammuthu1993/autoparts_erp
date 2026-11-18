package com.autoparts.erp.ai.rag.retrieval;

/**
 * Typed exception thrown when knowledge retrieval fails due to database errors,
 * infrastructure failures, or unexpected query execution errors.
 */
public class KnowledgeRetrievalException extends RuntimeException {

    public KnowledgeRetrievalException(String message) {
        super(message);
    }

    public KnowledgeRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
