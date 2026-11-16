package com.autoparts.erp.ai.service;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;

public class AiModelCallTimingAdvisor implements CallAdvisor {

    private final AiPerformanceMetrics performanceMetrics;

    public AiModelCallTimingAdvisor(AiPerformanceMetrics performanceMetrics) {
        this.performanceMetrics = performanceMetrics;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain
    ) {
        long startedAtNanos = System.nanoTime();
        ChatClientResponse response = null;
        Throwable failure = null;

        try {
            response = chain.nextCall(request);
            return response;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            performanceMetrics.recordModelCall(
                    System.nanoTime() - startedAtNanos,
                    response,
                    failure);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public String getName() {
        return "AiModelCallTimingAdvisor";
    }
}
