package com.autoparts.erp.ai.service;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Low-cardinality Micrometer instrumentation for the ERP AI pipeline.
 * Never tag metrics with user IDs, conversation IDs, prompts, or SQL values.
 */
@Component
public class AiMetricsService {

    private final MeterRegistry registry;

    @Autowired
    public AiMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    /** No-op instance for direct unit construction outside the Spring container. */
    public AiMetricsService() {
        this.registry = null;
    }

    public <T> T recordNode(String node, Supplier<T> operation) {
        long started = System.nanoTime();
        try {
            return operation.get();
        } finally {
            recordDuration("ai.node.duration", "node", node, System.nanoTime() - started);
        }
    }

    public void recordRequestDuration(long durationNanos) {
        recordDuration("ai.request.duration", "component", "chat", durationNanos);
        increment("ai.request.attempts", "component", "chat");
    }

    public void recordProviderDuration(long durationNanos) {
        recordDuration("ai.provider.duration", "provider", "sarvam", durationNanos);
    }

    public void recordTool(String tool, String status, long durationNanos, int rowsReturned) {
        recordDuration("ai.tool.duration", "tool", safeTag(tool), durationNanos);
        increment("ai.tool.executions", "status", safeTag(status));
        if (registry != null) {
            DistributionSummary.builder("ai.tool.rows")
                    .tag("tool", safeTag(tool))
                    .register(registry)
                    .record(Math.max(0, rowsReturned));
        }
    }

    public void recordProviderRetry(String reason) {
        increment("ai.provider.retries", "reason", safeTag(reason));
    }

    public void recordProviderFailure() {
        increment("ai.provider.failures", "component", "chat");
    }

    public void recordAuditFailure() {
        increment("ai.audit.write.failures", "component", "tool");
    }

    public void recordTokenUsage(ChatResponse response) {
        if (registry == null || response == null || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.getMetadata().getUsage();
        recordToken("prompt", usage.getPromptTokens());
        recordToken("completion", usage.getCompletionTokens());
        recordToken("total", usage.getTotalTokens());
    }

    private void recordToken(String kind, Number value) {
        if (value == null) {
            return;
        }
        DistributionSummary.builder("ai.token.usage")
                .tag("kind", kind)
                .register(registry)
                .record(Math.max(0, value.doubleValue()));
    }

    private void recordDuration(String name, String tag, String value, long nanos) {
        if (registry == null) {
            return;
        }
        Timer.builder(name)
                .tag(tag, safeTag(value))
                .register(registry)
                .record(Duration.ofNanos(Math.max(0, nanos)));
    }

    private void increment(String name, String tag, String value) {
        if (registry == null) {
            return;
        }
        Counter.builder(name)
                .tag(tag, safeTag(value))
                .register(registry)
                .increment();
    }

    private static String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }
}
