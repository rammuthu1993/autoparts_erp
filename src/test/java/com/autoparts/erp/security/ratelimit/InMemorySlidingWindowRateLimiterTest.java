package com.autoparts.erp.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("In-Memory Sliding Window Rate Limiter Tests")
class InMemorySlidingWindowRateLimiterTest {

    private static final long ONE_SEC_NANOS = 1_000_000_000L;

    @Test
    @DisplayName("Allows requests up to configured limit and correctly reports remaining permits")
    void shouldAllowRequestsUpToConfiguredLimit() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        var limiter = new InMemorySlidingWindowRateLimiter(
                3, 60, 5, 60, 10, 60, 1000, clock::get
        );

        RateLimitResult r1 = limiter.tryAcquire("192.168.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertTrue(r1.allowed());
        assertEquals(2, r1.remainingPermits());
        assertEquals(3, r1.limit());

        RateLimitResult r2 = limiter.tryAcquire("192.168.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertTrue(r2.allowed());
        assertEquals(1, r2.remainingPermits());

        RateLimitResult r3 = limiter.tryAcquire("192.168.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertTrue(r3.allowed());
        assertEquals(0, r3.remainingPermits());

        // 4th attempt should be rejected
        RateLimitResult r4 = limiter.tryAcquire("192.168.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertFalse(r4.allowed());
        assertEquals(0, r4.remainingPermits());
        assertEquals(60, r4.retryAfterSeconds());
    }

    @Test
    @DisplayName("Slides window and recovers permits after configured window expires")
    void shouldSlideWindowAndRecoverPermits() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        var limiter = new InMemorySlidingWindowRateLimiter(
                2, 10, 5, 60, 10, 60, 1000, clock::get
        );

        assertTrue(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());
        clock.addAndGet(2 * ONE_SEC_NANOS); // +2s
        assertTrue(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());

        // Exhausted at t = 3s
        RateLimitResult rejected = limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertFalse(rejected.allowed());
        assertEquals(8, rejected.retryAfterSeconds()); // oldest at 1s, window 10s -> expires at 11s (11 - 3 = 8s)

        // Advance clock past the first request expiration (t = 12s)
        clock.set(12 * ONE_SEC_NANOS);
        RateLimitResult recovered = limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertTrue(recovered.allowed(), "Should allow request after first timestamp expires");
    }

    @Test
    @DisplayName("Maintains independent limits per IP address")
    void shouldIsolateLimitsPerIp() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        var limiter = new InMemorySlidingWindowRateLimiter(
                2, 60, 5, 60, 10, 60, 1000, clock::get
        );

        // Exhaust IP 1
        assertTrue(limiter.tryAcquire("1.1.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());
        assertTrue(limiter.tryAcquire("1.1.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());
        assertFalse(limiter.tryAcquire("1.1.1.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());

        // IP 2 must still have full allowance
        RateLimitResult ip2Result = limiter.tryAcquire("2.2.2.2", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
        assertTrue(ip2Result.allowed());
        assertEquals(1, ip2Result.remainingPermits());
    }

    @Test
    @DisplayName("Maintains independent categories for login versus refresh")
    void shouldEnforceIndependentLimitsForLoginAndRefresh() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        var limiter = new InMemorySlidingWindowRateLimiter(
                1, 60, 3, 60, 10, 60, 1000, clock::get
        );

        // Exhaust login
        assertTrue(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());
        assertFalse(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN).allowed());

        // Refresh on same IP is independently tracked
        assertTrue(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_REFRESH).allowed());
        assertTrue(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_REFRESH).allowed());
        assertTrue(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_REFRESH).allowed());
        assertFalse(limiter.tryAcquire("10.0.0.1", InMemorySlidingWindowRateLimiter.CATEGORY_REFRESH).allowed());
    }

    @Test
    @DisplayName("Thread safety: Concurrent requests from same IP strictly enforce permit ceiling without over-allocation")
    void shouldHandleHighConcurrencySafely() throws InterruptedException, ExecutionException {
        final int limit = 10;
        final int threads = 40;
        var limiter = new InMemorySlidingWindowRateLimiter(
                limit, 60, 20, 60, 30, 60, 1000, System::nanoTime
        );

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                RateLimitResult res = limiter.tryAcquire("198.51.100.10", InMemorySlidingWindowRateLimiter.CATEGORY_LOGIN);
                if (res.allowed()) {
                    allowedCount.incrementAndGet();
                    return true;
                } else {
                    rejectedCount.incrementAndGet();
                    return false;
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(limit, allowedCount.get(), "Exactly configured limit must be permitted under race conditions");
        assertEquals(threads - limit, rejectedCount.get(), "All excess requests must be rejected");
    }
}
