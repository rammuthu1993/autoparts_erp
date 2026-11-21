package com.autoparts.erp.ai.exception;

/**
 * Raised when an authenticated user exceeds the AI request budget or the
 * application-wide AI execution capacity is exhausted.
 */
public final class AiRateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public AiRateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
