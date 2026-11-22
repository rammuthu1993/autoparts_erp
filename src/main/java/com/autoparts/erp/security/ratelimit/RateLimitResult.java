package com.autoparts.erp.security.ratelimit;

/**
 * Result of a rate limiting evaluation.
 *
 * @param allowed whether the request is allowed to proceed
 * @param retryAfterSeconds seconds the client must wait before retrying (0 if allowed)
 * @param remainingPermits remaining request permits in the active window
 * @param limit configured maximum permits in the window
 */
public record RateLimitResult(
        boolean allowed,
        long retryAfterSeconds,
        int remainingPermits,
        int limit
) {
    public static RateLimitResult permitted(int remainingPermits, int limit) {
        return new RateLimitResult(true, 0, Math.max(0, remainingPermits), limit);
    }

    public static RateLimitResult rejected(long retryAfterSeconds, int limit) {
        return new RateLimitResult(false, Math.max(1, retryAfterSeconds), 0, limit);
    }
}
