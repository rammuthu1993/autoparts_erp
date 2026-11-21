package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class AiResilienceServiceTest {

    @Test
    void retriesConnectionFailuresWithBoundedAttempts() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger sleeps = new AtomicInteger();

        AiResilienceService service = new AiResilienceService(
                3, Duration.ofMillis(1), Duration.ofMillis(4), 0,
                Duration.ofSeconds(5), new AiMetricsService(),
                millis -> sleeps.incrementAndGet());

        String result = service.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("connection reset", new IOException("reset"));
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(2, sleeps.get());
    }

    @Test
    void doesNotRetryClientOrValidationFailures() {
        AtomicInteger calls = new AtomicInteger();
        AiResilienceService service = new AiResilienceService(
                3, Duration.ZERO, Duration.ofMillis(4), 0,
                Duration.ofSeconds(5), new AiMetricsService(), millis -> { });

        assertThrows(IllegalArgumentException.class, () -> service.execute(() -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("invalid request");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void honorsRetryAfterHeaderForRateLimitResponses() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger requestedDelay = new AtomicInteger();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "2");

        AiResilienceService service = new AiResilienceService(
                3, Duration.ofMillis(1), Duration.ofMillis(4), 0,
                Duration.ofSeconds(5), new AiMetricsService(),
                millis -> requestedDelay.set(Math.toIntExact(millis)));

        String result = service.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new HttpClientErrorException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        headers,
                        new byte[0],
                        StandardCharsets.UTF_8);
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
        assertEquals(2000, requestedDelay.get());
    }

    @Test
    void doesNotRetryHttpBadRequest() {
        AtomicInteger calls = new AtomicInteger();
        AiResilienceService service = new AiResilienceService(
                3, Duration.ZERO, Duration.ofMillis(4), 0,
                Duration.ofSeconds(5), new AiMetricsService(), millis -> { });

        assertThrows(HttpClientErrorException.class, () -> service.execute(() -> {
            calls.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotRetryAfterRetryBudgetIsExhausted() {
        AtomicInteger calls = new AtomicInteger();
        AiResilienceService service = new AiResilienceService(
                3, Duration.ofSeconds(2), Duration.ofSeconds(4), 0,
                Duration.ofMillis(1), new AiMetricsService(), millis -> { });

        assertThrows(ResourceAccessException.class, () -> service.execute(() -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("connection reset");
        }));
        assertEquals(1, calls.get());
    }
}
