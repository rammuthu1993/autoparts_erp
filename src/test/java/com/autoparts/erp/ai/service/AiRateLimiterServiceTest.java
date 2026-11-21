package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.autoparts.erp.ai.exception.AiRateLimitExceededException;

class AiRateLimiterServiceTest {

    @Test
    void enforcesSlidingWindowPerUserAndAllowsAfterExpiry() {
        AtomicLong clock = new AtomicLong(0);
        AiRateLimiterService limiter = new AiRateLimiterService(
                2, 60, 1, 10, clock::get);

        limiter.checkUserRequest("user-a");
        clock.addAndGet(10_000_000_000L);
        limiter.checkUserRequest("user-a");

        AiRateLimitExceededException rejected = assertThrows(
                AiRateLimitExceededException.class,
                () -> limiter.checkUserRequest("user-a"));
        assertEquals(50, rejected.retryAfterSeconds());

        clock.addAndGet(50_000_000_000L);
        assertDoesNotThrow(() -> limiter.checkUserRequest("user-a"));
        assertDoesNotThrow(() -> limiter.checkUserRequest("user-b"));
    }

    @Test
    void globalExecutionPermitIsBoundedAndReleasedExactlyOnce() {
        AiRateLimiterService limiter = new AiRateLimiterService(
                20, 60, 1, 10, System::nanoTime);

        AiRateLimiterService.GlobalPermit permit = limiter.acquireGlobalExecution();
        assertEquals(0, limiter.availableExecutionSlots());

        assertThrows(AiRateLimitExceededException.class,
                limiter::acquireGlobalExecution);

        permit.close();
        permit.close();
        assertEquals(1, limiter.availableExecutionSlots());

        try (AiRateLimiterService.GlobalPermit ignored = limiter.acquireGlobalExecution()) {
            assertEquals(0, limiter.availableExecutionSlots());
        }
        assertEquals(1, limiter.availableExecutionSlots());
    }

    @Test
    void rejectsBlankUserSubjects() {
        AiRateLimiterService limiter = new AiRateLimiterService(
                20, 60, 1, 10, System::nanoTime);
        assertThrows(IllegalArgumentException.class,
                () -> limiter.checkUserRequest(" "));
    }
}
