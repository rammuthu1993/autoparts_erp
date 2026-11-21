package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.autoparts.erp.ai.exception.AiRateLimitExceededException;

/** Stress test for the configured per-JVM graph admission semaphore. */
class ErpAiConcurrencyLoadTest {

    @Test
    void admitsAtMostTenOfFortySimultaneousExecutionsAndReleasesEveryPermit() throws Exception {
        int burstSize = 40;
        int configuredCapacity = 10;
        AiRateLimiterService limiter = new AiRateLimiterService(
                100, 60, configuredCapacity, 100, System::nanoTime);

        ExecutorService workers = Executors.newFixedThreadPool(burstSize);
        CountDownLatch ready = new CountDownLatch(burstSize);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(burstSize);
        CountDownLatch releaseAccepted = new CountDownLatch(1);
        CountDownLatch acceptedEntered = new CountDownLatch(configuredCapacity);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peakActive = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < burstSize; i++) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    try (AiRateLimiterService.GlobalPermit ignored = limiter.acquireGlobalExecution()) {
                        accepted.incrementAndGet();
                        int nowActive = active.incrementAndGet();
                        peakActive.accumulateAndGet(nowActive, Math::max);
                        acceptedEntered.countDown();
                        attempted.countDown();
                        try {
                            releaseAccepted.await();
                        } finally {
                            active.decrementAndGet();
                        }
                    } catch (AiRateLimitExceededException ex) {
                        rejected.incrementAndGet();
                        attempted.countDown();
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();
            assertTrue(acceptedEntered.await(5, TimeUnit.SECONDS), "Capacity was not fully exercised");
            assertTrue(attempted.await(5, TimeUnit.SECONDS), "Not all burst requests attempted admission");

            assertEquals(configuredCapacity, accepted.get());
            assertEquals(burstSize - configuredCapacity, rejected.get());
            assertEquals(configuredCapacity, peakActive.get());
            assertEquals(0, limiter.availableExecutionSlots());
        } finally {
            start.countDown();
            releaseAccepted.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(0, active.get());
        assertEquals(configuredCapacity, limiter.availableExecutionSlots(),
                "All global permits must be returned after execution completion");
    }
}
