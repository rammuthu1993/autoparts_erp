package com.autoparts.erp.security.ratelimit;

/**
 * Rate Limiter interface for guarding sensitive endpoints against brute-force attacks and DDoS.
 *
 * <p>Default in-process implementation is {@link InMemorySlidingWindowRateLimiter}.
 * In distributed or multi-instance deployments, this contract can be backed by a distributed
 * store (e.g., Redis via Bucket4j or Redisson) without altering downstream security filters
 * or authentication controllers.</p>
 */
public interface RateLimiter {

    /**
     * Attempts to acquire a single request permit for the specified key within an endpoint category.
     *
     * @param key the client key (e.g. client IP address)
     * @param category the endpoint category (e.g. "auth-login", "auth-refresh")
     * @return {@link RateLimitResult} containing status, remaining permits, and retry-after duration
     */
    RateLimitResult tryAcquire(String key, String category);
}
