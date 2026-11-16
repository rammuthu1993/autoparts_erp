package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

class AiPerformanceMetricsTest {

    @Test
    void recordsIndividualModelCallsAndAggregatedUsage() {
        AiPerformanceMetrics metrics = new AiPerformanceMetrics();
        AiPerformanceMetrics.ExecutionMetrics execution =
                metrics.createExecution(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        System.nanoTime());
        ChatClientResponse toolCallResponse = response(true, 40, 3);
        ChatClientResponse finalResponse = response(false, 60, 7);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any(ChatClientRequest.class)))
                .thenReturn(toolCallResponse, finalResponse);
        AiModelCallTimingAdvisor advisor =
                new AiModelCallTimingAdvisor(metrics);
        ChatClientRequest request = mock(ChatClientRequest.class);

        try (AiPerformanceMetrics.Scope ignored = metrics.activate(execution)) {
            assertSame(toolCallResponse, advisor.adviseCall(request, chain));
            assertSame(finalResponse, advisor.adviseCall(request, chain));

            assertEquals("result", metrics.measureTool("lookup", () -> "result"));
            assertEquals(
                    List.of("value"),
                    metrics.measureDatabaseQuery(
                            "products.search",
                            () -> List.of("value")));
        }
        metrics.measureTool("outsideExecution", () -> "ignored");

        assertEquals(2, execution.modelCallCount());
        assertEquals(100L, execution.promptTokens());
        assertEquals(10L, execution.completionTokens());
        assertEquals("sarvam-105b", execution.modelName());
        assertTrue(execution.totalModelCallNanos() > 0);
        assertTrue(execution.finalResponseGenerationNanos() > 0);
        assertEquals(1, execution.toolCount());
        assertEquals(List.of("lookup"), execution.toolNames());
        assertTrue(execution.totalToolExecutionNanos() > 0);
        assertEquals(1, execution.databaseQueryCount());
        assertEquals(List.of("products.search"), execution.databaseQueryNames());
        assertTrue(execution.totalDatabaseQueryNanos() > 0);
    }

    private static ChatClientResponse response(
            boolean hasToolCalls,
            int promptTokens,
            int completionTokens
    ) {
        ChatClientResponse response = mock(ChatClientResponse.class);
        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);

        when(response.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.hasToolCalls()).thenReturn(hasToolCalls);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getModel()).thenReturn("sarvam-105b");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);

        return response;
    }
}
