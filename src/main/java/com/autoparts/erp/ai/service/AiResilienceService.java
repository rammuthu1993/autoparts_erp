package com.autoparts.erp.ai.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Bounded retry policy for transient Sarvam/OpenAI-compatible provider failures.
 *
 * <p>Retries 429, 502, 503, 504 and connection-level failures only. Client
 * validation/authentication/authorization errors are never retried. The
 * existing agent execution timeout remains the outer cancellation boundary.</p>
 */
@Service
public class AiResilienceService {

    private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(429, 502, 503, 504);

    private final int maxRetries;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double jitter;
    private final Duration maxElapsed;
    private final Duration providerTimeout;
    private final AiMetricsService metrics;

    @Autowired
    public AiResilienceService(
            @Value("${app.ai.resilience.max-retries:3}") int maxRetries,
            @Value("${app.ai.resilience.base-delay:1s}") Duration baseDelay,
            @Value("${app.ai.resilience.max-delay:4s}") Duration maxDelay,
            @Value("${app.ai.resilience.jitter:0.25}") double jitter,
            @Value("${ai.agent.execution-timeout:60s}") Duration maxElapsed,
            @Value("${spring.ai.openai.timeout:45s}") Duration providerTimeout,
            AiMetricsService metrics) {
        this(maxRetries, baseDelay, maxDelay, jitter, maxElapsed, providerTimeout,
                metrics, Thread::sleep);
    }

    AiResilienceService(
            int maxRetries,
            Duration baseDelay,
            Duration maxDelay,
            double jitter,
            Duration maxElapsed,
            AiMetricsService metrics,
            Sleeper sleeper) {
        this(maxRetries, baseDelay, maxDelay, jitter, maxElapsed,
                Duration.ZERO, metrics, sleeper);
    }

    private AiResilienceService(
            int maxRetries,
            Duration baseDelay,
            Duration maxDelay,
            double jitter,
            Duration maxElapsed,
            Duration providerTimeout,
            AiMetricsService metrics,
            Sleeper sleeper) {
        if (maxRetries < 0 || maxRetries > 3
                || baseDelay == null || baseDelay.isNegative()
                || maxDelay == null || maxDelay.isNegative()
                || jitter < 0 || jitter > 0.5
                || maxElapsed == null || maxElapsed.isNegative() || maxElapsed.isZero()
                || providerTimeout == null || providerTimeout.isNegative()) {
            throw new IllegalArgumentException("Invalid AI resilience configuration");
        }
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.jitter = jitter;
        this.maxElapsed = maxElapsed;
        this.providerTimeout = providerTimeout;
        this.metrics = metrics == null ? new AiMetricsService() : metrics;
        this.sleeper = sleeper;
    }

    private final Sleeper sleeper;

    public <T> T execute(Supplier<T> operation) {
        long started = System.nanoTime();
        int retries = 0;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("AI provider call interrupted");
            }

            long attemptStarted = System.nanoTime();

            try {
                T response = operation.get();
                metrics.recordProviderDuration(System.nanoTime() - attemptStarted);
                return response;

            } catch (RuntimeException failure) {
                metrics.recordProviderDuration(System.nanoTime() - attemptStarted);

                if (!isRetryable(failure) || retries >= maxRetries) {
                    if (isRetryable(failure)) {
                        metrics.recordProviderFailure();
                    }
                    throw failure;
                }

                // Capture the current retry count in an effectively final variable.
                final int retryIndex = retries;

                Duration delay = retryAfter(failure)
                        .orElseGet(() -> jitteredBackoff(retryIndex));

                long elapsed = System.nanoTime() - started;
                long remaining = maxElapsed.toNanos() - elapsed;
                long nextAttemptBudget = delay.toNanos() + providerTimeout.toNanos();

                if (remaining <= 0 || nextAttemptBudget >= remaining) {
                    metrics.recordProviderFailure();
                    throw failure;
                }

                metrics.recordProviderRetry(retryReason(failure));
                retries++;

                try {
                    sleeper.sleep(delay.toMillis());

                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    CancellationException cancelled =
                            new CancellationException("AI provider retry interrupted");

                    cancelled.initCause(interrupted);
                    throw cancelled;
                }
            }
        }
    }

    boolean isRetryable(Throwable failure) {
        // TLS/certificate failures are configuration/security problems, not transient retries.
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SSLException) {
                return false;
            }
        }
        // An HTTP response takes precedence over any wrapper exception in its cause chain.
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException responseException) {
                return RETRYABLE_HTTP_STATUSES.contains(responseException.getStatusCode().value());
            }
        }
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ResourceAccessException
                    || current instanceof SocketException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException
                    || current instanceof UnknownHostException) {
                return true;
            }
            if (current instanceof IOException ioException) {
                String message = ioException.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase(java.util.Locale.ROOT);
                    if (normalized.contains("connection reset")
                            || normalized.contains("connection refused")
                            || normalized.contains("broken pipe")
                            || normalized.contains("timed out")
                            || normalized.contains("no route to host")
                            || normalized.contains("network is unreachable")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Optional<Duration> retryAfter(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException responseException) {
                HttpHeaders headers = responseException.getResponseHeaders();
                if (headers == null) {
                    return Optional.empty();
                }
                String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
                if (value == null || value.isBlank()) {
                    return Optional.empty();
                }
                try {
                    Duration requested = Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
                    return Optional.of(requested.compareTo(maxElapsed) > 0 ? maxElapsed : requested);
                } catch (NumberFormatException | ArithmeticException ignored) {
                    try {
                        Duration until = Duration.between(
                                ZonedDateTime.now(),
                                ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME));
                        Duration requested = until.isNegative() ? Duration.ZERO : until;
                        return Optional.of(requested.compareTo(maxElapsed) > 0 ? maxElapsed : requested);
                    } catch (DateTimeParseException ignoredDate) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Duration jitteredBackoff(int retryIndex) {
        long multiplier = 1L << Math.min(retryIndex, 10);
        Duration exponential = baseDelay.multipliedBy(multiplier);
        if (exponential.compareTo(maxDelay) > 0) {
            exponential = maxDelay;
        }
        if (jitter == 0 || exponential.isZero()) {
            return exponential;
        }
        double factor = ThreadLocalRandom.current().nextDouble(1.0 - jitter, 1.0 + jitter);
        return Duration.ofMillis(Math.max(0, Math.round(exponential.toMillis() * factor)));
    }

    private String retryReason(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException responseException) {
                return "http_" + responseException.getStatusCode().value();
            }
        }
        return "connection";
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
