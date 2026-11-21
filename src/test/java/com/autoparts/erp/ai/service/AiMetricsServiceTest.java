package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AiMetricsServiceTest {

    @Test
    void recordsLowCardinalityNodeAndRequestMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetricsService metrics = new AiMetricsService(registry);

        String result = metrics.recordNode("classify", () -> "done");
        metrics.recordRequestDuration(1_000_000L);
        metrics.recordProviderDuration(1_500_000L);
        metrics.recordTool("PRODUCT_SEARCH", "SUCCESS", 2_000_000L, 3);

        assertEquals("done", result);
        assertEquals(1, registry.get("ai.node.duration")
                .tag("node", "classify").timer().count());
        assertEquals(1, registry.get("ai.request.duration")
                .tag("component", "chat").timer().count());
        assertEquals(1, registry.get("ai.provider.duration")
                .tag("provider", "sarvam").timer().count());
        assertEquals(1, registry.get("ai.tool.duration")
                .tag("tool", "product_search").timer().count());
        assertEquals(1, registry.get("ai.tool.executions")
                .tag("status", "success").counter().count(), 0.0001);
    }
}
