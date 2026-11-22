package com.autoparts.erp.security.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * In-memory sliding-window rate limiter.
 *
 * <p>Tracks client request timestamps over a true rolling window per IP and category.
 * Safe for concurrent access in single-instance deployments. Bounded by {@code maxTrackedIps}
 * with automatic eviction to prevent unbounded memory growth.</p>
 */
@Service
public class InMemorySlidingWindowRateLimiter implements RateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public static final String CATEGORY_LOGIN = "auth-login";
    public static final String CATEGORY_REFRESH = "auth-refresh";

    private final int loginMaxRequests;
    private final long loginWindowNanos;
    private final int refreshMaxRequests;
    private final long refreshWindowNanos;
    private final int defaultMaxRequests;
    private final long defaultWindowNanos;
    private final int maxTrackedKeys;
    private final LongSupplier nanoTime;

    private final Map<String, ArrayDeque<Long>> keyTimestamps = new HashMap<>();

    @Autowired
    public InMemorySlidingWindowRateLimiter(
            @Value("${app.security.rate-limit.login.max-requests:10}") int loginMaxRequests,
            @Value("${app.security.rate-limit.login.window-seconds:60}") int loginWindowSeconds,
            @Value("${app.security.rate-limit.refresh.max-requests:20}") int refreshMaxRequests,
            @Value("${app.security.rate-limit.refresh.window-seconds:60}") int refreshWindowSeconds,
            @Value("${app.security.rate-limit.max-tracked-ips:10000}") int maxTrackedIps
    ) {
        this(
                loginMaxRequests,
                loginWindowSeconds,
                refreshMaxRequests,
                refreshWindowSeconds,
                30,
                60,
                maxTrackedIps,
                System::nanoTime
        );
    }

    public InMemorySlidingWindowRateLimiter(
            int loginMaxRequests,
            int loginWindowSeconds,
            int refreshMaxRequests,
            int refreshWindowSeconds,
            int defaultMaxRequests,
            int defaultWindowSeconds,
            int maxTrackedKeys,
            LongSupplier nanoTime
    ) {
        this.loginMaxRequests = Math.max(1, loginMaxRequests);
        this.loginWindowNanos = Math.multiplyExact((long) Math.max(1, loginWindowSeconds), NANOS_PER_SECOND);
        this.refreshMaxRequests = Math.max(1, refreshMaxRequests);
        this.refreshWindowNanos = Math.multiplyExact((long) Math.max(1, refreshWindowSeconds), NANOS_PER_SECOND);
        this.defaultMaxRequests = Math.max(1, defaultMaxRequests);
        this.defaultWindowNanos = Math.multiplyExact((long) Math.max(1, defaultWindowSeconds), NANOS_PER_SECOND);
        this.maxTrackedKeys = Math.max(100, maxTrackedKeys);
        this.nanoTime = (nanoTime != null) ? nanoTime : System::nanoTime;
    }

    @Override
    public synchronized RateLimitResult tryAcquire(String key, String category) {
        if (key == null || key.isBlank()) {
            key = "unknown";
        }
        String normalizedCategory = (category != null && !category.isBlank()) ? category.trim() : "default";
        String bucketKey = normalizedCategory + ":" + key.trim();

        int maxRequests = resolveMaxRequests(normalizedCategory);
        long windowNanos = resolveWindowNanos(normalizedCategory);
        long now = nanoTime.getAsLong();
        long cutoff = now - windowNanos;

        ArrayDeque<Long> timestamps = keyTimestamps.get(bucketKey);
        if (timestamps == null) {
            if (keyTimestamps.size() >= maxTrackedKeys) {
                pruneExpired(now);
                if (keyTimestamps.size() >= maxTrackedKeys) {
                    // Evict any arbitrary entry to maintain strict bound
                    Iterator<String> it = keyTimestamps.keySet().iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
            }
            timestamps = new ArrayDeque<>();
            keyTimestamps.put(bucketKey, timestamps);
        }

        // Evict timestamps outside the sliding window
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.removeFirst();
        }

        if (timestamps.size() >= maxRequests) {
            long oldestTimestamp = timestamps.peekFirst();
            long waitNanos = Math.max(1L, oldestTimestamp + windowNanos - now);
            long retryAfterSeconds = Math.max(1L, (waitNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND);
            return RateLimitResult.rejected(retryAfterSeconds, maxRequests);
        }

        timestamps.addLast(now);
        int remaining = maxRequests - timestamps.size();
        return RateLimitResult.permitted(remaining, maxRequests);
    }

    private int resolveMaxRequests(String category) {
        if (CATEGORY_LOGIN.equalsIgnoreCase(category)) {
            return loginMaxRequests;
        }
        if (CATEGORY_REFRESH.equalsIgnoreCase(category)) {
            return refreshMaxRequests;
        }
        return defaultMaxRequests;
    }

    private long resolveWindowNanos(String category) {
        if (CATEGORY_LOGIN.equalsIgnoreCase(category)) {
            return loginWindowNanos;
        }
        if (CATEGORY_REFRESH.equalsIgnoreCase(category)) {
            return refreshWindowNanos;
        }
        return defaultWindowNanos;
    }

    private void pruneExpired(long now) {
        Iterator<Map.Entry<String, ArrayDeque<Long>>> iterator = keyTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ArrayDeque<Long>> entry = iterator.next();
            String bucketKey = entry.getKey();
            String cat = bucketKey.contains(":") ? bucketKey.substring(0, bucketKey.indexOf(':')) : "default";
            long window = resolveWindowNanos(cat);
            long cutoff = now - window;

            ArrayDeque<Long> queue = entry.getValue();
            while (!queue.isEmpty() && queue.peekFirst() <= cutoff) {
                queue.removeFirst();
            }
            if (queue.isEmpty()) {
                iterator.remove();
            }
        }
    }
}
