
package com.autoparts.erp.ai.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.autoparts.erp.ai.tools.ProductLookupTool;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;

import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;

@Service
public class ErpAiAgentService {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiAgentService.class);

    private final CompiledGraph<AgentExecutor.State> workflow;

//    private final Map<UUID,
//            AsyncGenerator.Cancellable<NodeOutput<AgentExecutor.State>>>
//            activeExecutions = new ConcurrentHashMap<>();
    
    private final Map<UUID, ActiveExecution> activeExecutions = new ConcurrentHashMap<>();

    public ErpAiAgentService(
            ChatModel chatModel,
            ProductLookupTool productLookupTool) {

        try {
            var graph = AgentExecutor.builder()
                    .chatModel(chatModel)
                    .defaultSystem(loadSystemPrompt())
                    .toolsFromObject(productLookupTool)
                    .build();

            this.workflow = graph.compile();

        } catch (GraphStateException exception) {
            throw new IllegalStateException(
                    "Failed to initialize the ERP AI agent graph",
                    exception
            );
        }
    }

    public String chat(
            UUID conversationId,
            UUID runId,
            List<Message> messages) {
        if (conversationId == null) {
            throw new IllegalArgumentException(
                    "Conversation ID cannot be null"
            );
        }

        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Conversation messages cannot be empty"
            );
        }

        var config = RunnableConfig.builder()
                .threadId(conversationId.toString())
                .build();

        var result = workflow.stream(
                GraphInput.args(Map.of("messages", messages)),
                config
        );

        var execution = new ActiveExecution(runId, result);

        var existingExecution = activeExecutions.putIfAbsent(
                conversationId,
                execution
        );

        if (existingExecution != null) {
            throw new IllegalStateException(
                    "An AI execution is already active for this conversation"
            );
        }
        
        execution.timeoutTask = timeoutScheduler.schedule(() -> {

            if (execution.terminalClaimed.compareAndSet(false, true)) {

                execution.timedOut.set(true);

                log.warn(
                        "AI agent execution timeout reached. Conversation: {}, Run: {}",
                        conversationId,
                        runId
                );

                execution.generator().cancel(true);
            }

        }, executionTimeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            var state = result.stream()
                    .reduce((first, second) -> second)
                    .map(NodeOutput::state)
                    .orElseThrow(() ->
                            new IllegalStateException("Agent returned no result")
                    );
            
            if (!execution.terminalClaimed.compareAndSet(false, true)) {

                if (execution.isTimedOut()) {
                    throw new AiAgentExecutionTimeoutException(
                            "AI agent execution exceeded the configured timeout"
                    );
                }

                throw new CancellationException(
                        "AI agent execution was cancelled"
                );
            }

            if (result.isCancelled()) {
                throw new CancellationException(
                        "AI agent execution was cancelled"
                );
            }

            return state.lastMessage()
                    .map(AssistantMessage.class::cast)
                    .map(AssistantMessage::getText)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Agent returned no assistant response"
                            )
                    );

        } catch (RuntimeException | Error exception) {

            if (execution.isTimedOut()) {
                throw new AiAgentExecutionTimeoutException(
                        "AI agent execution exceeded the configured timeout",
                        exception
                );
            }

            if (!result.isCancelled()) {
                result.cancel(true);
            }

            throw exception;

        } finally {

            if (execution.timeoutTask != null) {
                execution.timeoutTask.cancel(false);
            }

            activeExecutions.remove(conversationId, execution);

            log.info(
                    "AI graph execution removed from active registry. Conversation: {}",
                    conversationId
            );
        }
    }

    public Optional<UUID> cancel(UUID conversationId) {

        ActiveExecution execution = activeExecutions.get(conversationId);

        if (execution == null) {
            return Optional.empty();
        }

        if (!execution.terminalClaimed.compareAndSet(false, true)) {
            return Optional.empty();
        }

        boolean cancelled = execution.generator().cancel(true);

        log.info(
                "AI cancellation requested. Conversation: {}, Run: {}, Accepted: {}",
                conversationId,
                execution.runId(),
                cancelled
        );

        return cancelled
                ? Optional.of(execution.runId())
                : Optional.empty();
    }

    private String loadSystemPrompt() {

        try {
            ClassPathResource resource =
                    new ClassPathResource(
                            "prompts/erp-system-prompt.st"
                    );

            try (var inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(
                        inputStream,
                        StandardCharsets.UTF_8
                );
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load ERP AI system prompt.",
                    exception
            );
        }
    }
    
    private static class ActiveExecution {

        private final UUID runId;

        private final AsyncGenerator.Cancellable<NodeOutput<AgentExecutor.State>> generator;

        private final AtomicBoolean timedOut = new AtomicBoolean(false);

        private final AtomicBoolean terminalClaimed = new AtomicBoolean(false);

        private ScheduledFuture<?> timeoutTask;

        private ActiveExecution(
                UUID runId,
                AsyncGenerator.Cancellable<NodeOutput<AgentExecutor.State>> generator
        ) {
            this.runId = runId;
            this.generator = generator;
        }

        public UUID runId() {
            return runId;
        }

        public AsyncGenerator.Cancellable<NodeOutput<AgentExecutor.State>> generator() {
            return generator;
        }

        public boolean isTimedOut() {
            return timedOut.get();
        }
    }
    
    @Value("${ai.agent.execution-timeout:60s}")
    private Duration executionTimeout;

    private final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "ai-agent-timeout");
                thread.setDaemon(true);
                return thread;
            });
    
    @PreDestroy
    public void shutdownTimeoutScheduler() {
        timeoutScheduler.shutdownNow();
    }
}
