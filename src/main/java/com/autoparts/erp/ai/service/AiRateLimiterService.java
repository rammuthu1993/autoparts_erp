package com.autoparts.erp.ai.service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.autoparts.erp.ai.exception.AiRateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bounded in-process sliding-window limiter.
 *
 * <p>Per-user requests are limited over a true rolling window, not a fixed
 * wall-clock bucket. Active graph executions are guarded by a bounded semaphore.
 * This implementation is intentionally single-instance; a multi-instance
 * deployment must move these counters/leases to a shared store.</p>
 */
@Service
public class AiRateLimiterService {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int requestsPerWindow;
    private final long windowNanos;
    private final int maxTrackedUsers;
    private final Semaphore activeExecutions;
    private final LongSupplier nanoTime;
    private final Map<String, ArrayDeque<Long>> userRequests = new HashMap<>();

    @Autowired
    public AiRateLimiterService(
            @Value("${app.ai.rate-limit.requests-per-minute:20}") int requestsPerMinute,
            @Value("${app.ai.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${app.ai.rate-limit.max-concurrent-executions:10}") int maxConcurrentExecutions,
            @Value("${app.ai.rate-limit.max-tracked-users:10000}") int maxTrackedUsers) {
        this(requestsPerMinute, windowSeconds, maxConcurrentExecutions, maxTrackedUsers, System::nanoTime);
    }

    AiRateLimiterService(
            int requestsPerWindow,
            int windowSeconds,
            int maxConcurrentExecutions,
            int maxTrackedUsers,
            LongSupplier nanoTime) {
        if (requestsPerWindow < 1 || windowSeconds < 1
                || maxConcurrentExecutions < 1 || maxTrackedUsers < 1) {
            throw new IllegalArgumentException("AI rate-limit settings must all be positive");
        }
        this.requestsPerWindow = requestsPerWindow;
        this.windowNanos = Math.multiplyExact((long) windowSeconds, NANOS_PER_SECOND);
        this.maxTrackedUsers = maxTrackedUsers;
        this.activeExecutions = new Semaphore(maxConcurrentExecutions, true);
        this.nanoTime = nanoTime;
    }

    /**
     * Consumes one request from the authenticated user's rolling window.
     * Invoke before writing a conversation/message or calling the provider.
     */
    public synchronized void checkUserRequest(String userSubject) {
        if (userSubject == null || userSubject.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required for AI rate limiting");
        }

        long now = nanoTime.getAsLong();
        ArrayDeque<Long> timestamps = userRequests.get(userSubject);
        if (timestamps == null) {
            if (userRequests.size() >= maxTrackedUsers) {
                pruneExpired(now);
                if (userRequests.size() >= maxTrackedUsers) {
                    throw new AiRateLimitExceededException(
                            "AI request capacity is temporarily busy. Please retry shortly.",
                            Math.max(1, windowNanos / NANOS_PER_SECOND));
                }
            }
            timestamps = new ArrayDeque<>();
            userRequests.put(userSubject, timestamps);
        }

        long cutoff = now - windowNanos;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.removeFirst();
        }

        if (timestamps.size() >= requestsPerWindow) {
            long waitNanos = Math.max(1L, timestamps.peekFirst() + windowNanos - now);
            long retryAfter = Math.max(1L,
                    (waitNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND);
            throw new AiRateLimitExceededException(
                    "AI request limit reached. Please wait before sending another message.",
                    retryAfter);
        }

        timestamps.addLast(now);
    }

    /**
     * Acquires a global execution slot without queueing HTTP request threads.
     * The returned permit must be closed in a finally block.
     */
    public GlobalPermit acquireGlobalExecution() {
        if (!activeExecutions.tryAcquire()) {
            throw new AiRateLimitExceededException(
                    "The AI assistant is handling its maximum number of concurrent requests. Please retry shortly.",
                    1);
        }
        return new GlobalPermit(activeExecutions);
    }

    public int availableExecutionSlots() {
        return activeExecutions.availablePermits();
    }

    private void pruneExpired(long now) {
        long cutoff = now - windowNanos;
        Iterator<Map.Entry<String, ArrayDeque<Long>>> iterator =
                userRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            ArrayDeque<Long> timestamps = iterator.next().getValue();
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.removeFirst();
            }
            if (timestamps.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static final class GlobalPermit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private GlobalPermit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }
}
