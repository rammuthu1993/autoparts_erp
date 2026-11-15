
package com.autoparts.erp.ai.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.concurrent.CompletableFuture;

import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;
import com.autoparts.erp.ai.exception.AiExecutionInProgressException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import org.springframework.ai.chat.model.ChatModel;
import com.autoparts.erp.ai.tools.ProductLookupTool;

@Service
public class ErpAiAgentService {

    public enum ExecutionStatus {
        RUNNING,
        COMPLETED,
        TIMED_OUT,
        CANCELLED,
        FAILED,
        SHUTDOWN
    }

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiAgentService.class);

    private final CompiledGraph<ErpAiGraphState> workflow;

    private final Duration executionTimeout;

    private final Map<UUID, ActiveExecution> activeExecutions =
            new ConcurrentHashMap<>();

    private final Object registryLock = new Object();

    private final AtomicBoolean shutdown =
            new AtomicBoolean(false);

    private final ScheduledExecutorService timeoutScheduler;

    // --------------------------------------------------
    // SPRING CONSTRUCTOR
    // --------------------------------------------------
    
    @Autowired
    public ErpAiAgentService(
            ErpAiGraphNodes graphNodes,
            @Value("${ai.agent.execution-timeout:60s}")
            Duration executionTimeout
    ) {
        try {
            this.workflow = buildWorkflow(graphNodes);
            this.executionTimeout = executionTimeout;
            this.timeoutScheduler = newTimeoutScheduler();

        } catch (GraphStateException exception) {

            throw new IllegalStateException(
                    "Failed to initialize custom ERP AI graph",
                    exception
            );
        }
    }
    
    public ErpAiAgentService(
            ChatModel chatModel,
            ProductLookupTool productLookupTool
    ) {
        this(
                new ErpAiGraphNodes(
                        chatModel,
                        productLookupTool,
                        new ErpAiIntentClassifier()
                ),
                Duration.ofSeconds(60)
        );
    }

    // --------------------------------------------------
    // TEST CONSTRUCTOR
    // --------------------------------------------------

    ErpAiAgentService(
            CompiledGraph<ErpAiGraphState> workflow,
            Duration executionTimeout,
            ScheduledExecutorService timeoutScheduler
    ) {
        this.workflow = workflow;
        this.executionTimeout = executionTimeout;
        this.timeoutScheduler = timeoutScheduler;
    }

    // --------------------------------------------------
    // GRAPH DEFINITION
    // --------------------------------------------------


private CompiledGraph<ErpAiGraphState> buildWorkflow(
        ErpAiGraphNodes nodes
) throws GraphStateException {

    var graph = new StateGraph<>(
            ErpAiGraphState.SCHEMA,
            ErpAiGraphState::new
    );

    // 1. Intent classification
    graph.addNode(
            "classify",
            (state, config) -> CompletableFuture.completedFuture(
                    nodes.classifyIntent(state, config)
            )
    );

    // 2. Product tool execution
    graph.addNode(
            "execute_tool",
            (state, config) -> CompletableFuture.completedFuture(
                    nodes.executeProductTool(state, config)
            )
    );

    // 3. General response generation
    graph.addNode(
            "generate_response",
            (state, config) -> CompletableFuture.completedFuture(
                    nodes.generateResponse(state, config)
            )
    );

    // 4. Unsupported request response
    graph.addNode(
            "unsupported_response",
            (state, config) -> CompletableFuture.completedFuture(
                    nodes.unsupportedResponse(state, config)
            )
    );

    // 5. Clarification response
    graph.addNode(
            "clarification_response",
            (state, config) -> CompletableFuture.completedFuture(
                    nodes.clarificationResponse(state, config)
            )
    );

    // 6. Response validation
    graph.addNode(
            "validate_response",
            (state, config) -> CompletableFuture.completedFuture(
                    nodes.validateResponse(state, config)
            )
    );

    // Entry point
    graph.addEdge(START, "classify");

    // Conditional routing
    graph.addConditionalEdges(
            "classify",
            state -> CompletableFuture.completedFuture(
                    ErpAiGraphNodes.routeIntent(state)
            ),
            Map.of(
                    "TOOL", "execute_tool",
                    "GENERAL", "generate_response",
                    "UNSUPPORTED", "unsupported_response",
                    "CLARIFICATION", "clarification_response"
            )
    );

    // Tool execution -> response generation
    graph.addEdge("execute_tool", "generate_response");

    // Response paths -> validation
    graph.addEdge("generate_response", "validate_response");
    graph.addEdge("unsupported_response", "validate_response");
    graph.addEdge("clarification_response", "validate_response");

    // Finish
    graph.addEdge("validate_response", END);

    return graph.compile();
}


    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    @PostConstruct
    public void validateExecutionTimeout() {

        if (executionTimeout == null
                || executionTimeout.isZero()
                || executionTimeout.isNegative()) {

            throw new IllegalStateException(
                    "The ai.agent.execution-timeout configuration must be positive"
            );
        }
    }

    // --------------------------------------------------
    // CHAT EXECUTION
    // --------------------------------------------------

    public String chat(
            UUID conversationId,
            UUID runId,
            List<Message> messages
    ) {

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

        var execution = new ActiveExecution(runId);

        synchronized (registryLock) {

            if (shutdown.get()) {
                throw new IllegalStateException(
                        "AI agent service is shutting down"
                );
            }

            if (activeExecutions.putIfAbsent(
                    conversationId,
                    execution
            ) != null) {

                throw new AiExecutionInProgressException(
                        "An AI execution is already active for this conversation"
                );
            }
        }

        try {

            execution.scheduleTimeout(
                    conversationId,
                    runId,
                    executionTimeout,
                    timeoutScheduler
            );

            execution.requireRunning();

            var config = RunnableConfig.builder()
                    .threadId(conversationId.toString())
                    .build();

            List<Map<String, String>> history =
                    convertHistory(messages);

            String currentMessage =
                    findLatestUserMessage(messages);

            Map<String, Object> graphInput = Map.of(
                    "conversationId", conversationId.toString(),
                    "currentMessage", currentMessage,
                    "history", history
            );

            var generator = execution.initializeGenerator(
                    () -> workflow.stream(
                            GraphInput.args(graphInput),
                            config
                    ),
                    conversationId
            );

            execution.requireRunning();

            var finalState = generator.stream()
                    .reduce((first, second) -> second)
                    .map(NodeOutput::state)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Custom ERP graph returned no result"
                            )
                    );

            String response = finalState.finalResponse();

            if (response == null || response.isBlank()) {
                throw new IllegalStateException(
                        "Custom ERP graph returned an empty response"
                );
            }

            if (!execution.complete()) {
                throwTerminalOutcome(
                        execution.status(),
                        null
                );
            }

            return response;

        } catch (RuntimeException | Error exception) {

            ExecutionStatus status = execution.status();

            if (status == ExecutionStatus.TIMED_OUT
                    || status == ExecutionStatus.CANCELLED
                    || status == ExecutionStatus.SHUTDOWN) {

                throwTerminalOutcome(status, exception);
            }

            if (!execution.markFailed()) {

                status = execution.status();

                if (status != ExecutionStatus.FAILED) {
                    throwTerminalOutcome(status, exception);
                }
            }

            execution.cancelGeneratorAfterFailure(conversationId);

            throw exception;

        } finally {

            if (execution.timeoutTask != null) {
                execution.timeoutTask.cancel(false);
            }

            activeExecutions.remove(
                    conversationId,
                    execution
            );

            log.info(
                    "Custom ERP graph execution removed. Conversation: {}, Run: {}",
                    conversationId,
                    runId
            );
        }
    }

    // --------------------------------------------------
    // CONVERT SPRING AI HISTORY
    // --------------------------------------------------

    private List<Map<String, String>> convertHistory(
            List<Message> messages
    ) {

        List<Map<String, String>> history = new ArrayList<>();

        for (Message message : messages) {

            String role;

            if (message instanceof UserMessage) {
                role = "USER";

            } else if (message instanceof AssistantMessage) {
                role = "ASSISTANT";

            } else {
                continue;
            }

            String content = message.getText();

            if (content == null) {
                content = "";
            }

            history.add(
                    Map.of(
                            "role", role,
                            "content", content
                    )
            );
        }

        return List.copyOf(history);
    }

    private String findLatestUserMessage(
            List<Message> messages
    ) {

        for (int i = messages.size() - 1; i >= 0; i--) {

            Message message = messages.get(i);

            if (message instanceof UserMessage) {

                String text = message.getText();

                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }

        throw new IllegalArgumentException(
                "No user message found in conversation history"
        );
    }

    // --------------------------------------------------
    // CANCELLATION
    // --------------------------------------------------

    public Optional<UUID> cancel(UUID conversationId) {

        ActiveExecution execution =
                activeExecutions.get(conversationId);

        if (execution == null) {
            return Optional.empty();
        }

        if (!execution.cancelByUser(conversationId)) {
            return Optional.empty();
        }

        log.info(
                "AI cancellation accepted. Conversation: {}, Run: {}",
                conversationId,
                execution.runId()
        );

        return Optional.of(execution.runId());
    }

    // --------------------------------------------------
    // TERMINAL OUTCOMES
    // --------------------------------------------------

    private static void throwTerminalOutcome(
            ExecutionStatus status,
            Throwable cause
    ) {

        switch (status) {

            case TIMED_OUT -> {

                if (cause instanceof AiAgentExecutionTimeoutException exception) {
                    throw exception;
                }

                throw new AiAgentExecutionTimeoutException(
                        "AI agent execution exceeded the configured timeout",
                        cause
                );
            }

            case CANCELLED -> {

                if (cause instanceof CancellationException exception) {
                    throw exception;
                }

                CancellationException cancellation =
                        new CancellationException(
                                "AI agent execution was cancelled"
                        );

                if (cause != null) {
                    cancellation.initCause(cause);
                }

                throw cancellation;
            }

            case SHUTDOWN -> {

                throw new IllegalStateException(
                        "AI agent execution was interrupted by application shutdown",
                        cause
                );
            }

            default -> throw new IllegalStateException(
                    "Unexpected terminal execution state: " + status,
                    cause
            );
        }
    }

    // --------------------------------------------------
    // ACTIVE EXECUTION MANAGEMENT
    // --------------------------------------------------

    private static class ActiveExecution {

        private final UUID runId;

        private final Object lifecycleLock = new Object();

        private final AtomicReference<ExecutionStatus> terminalState =
                new AtomicReference<>(ExecutionStatus.RUNNING);

        private volatile AsyncGenerator.Cancellable<
                NodeOutput<ErpAiGraphState>
        > generator;

        private volatile ScheduledFuture<?> timeoutTask;

        private ActiveExecution(UUID runId) {
            this.runId = runId;
        }

        private UUID runId() {
            return runId;
        }

        private ExecutionStatus status() {
            return terminalState.get();
        }

        private boolean complete() {

            synchronized (lifecycleLock) {

                boolean completed = terminalState.compareAndSet(
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.COMPLETED
                );

                if (completed) {
                    log.info(
                            "AI graph execution completed. Run: {}",
                            runId
                    );
                }

                return completed;
            }
        }

        private boolean markFailed() {

            synchronized (lifecycleLock) {

                return terminalState.compareAndSet(
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.FAILED
                );
            }
        }

        private void requireRunning() {

            ExecutionStatus current = status();

            if (current != ExecutionStatus.RUNNING) {
                throwTerminalOutcome(current, null);
            }
        }

        private AsyncGenerator.Cancellable<
                NodeOutput<ErpAiGraphState>
        > initializeGenerator(
                Supplier<AsyncGenerator.Cancellable<
                        NodeOutput<ErpAiGraphState>
                >> supplier,
                UUID conversationId
        ) {

            var initialized = supplier.get();

            ExecutionStatus current;

            synchronized (lifecycleLock) {

                generator = initialized;
                current = terminalState.get();
            }

            if (initialized != null
                    && current != ExecutionStatus.RUNNING) {

                cancelGeneratorForTerminal(
                        initialized,
                        current,
                        conversationId
                );
            }

            return initialized;
        }

        private boolean cancelByUser(UUID conversationId) {

            AsyncGenerator.Cancellable<
                    NodeOutput<ErpAiGraphState>
            > activeGenerator;

            synchronized (lifecycleLock) {

                if (!terminalState.compareAndSet(
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.CANCELLED
                )) {
                    return false;
                }

                activeGenerator = generator;
            }

            if (activeGenerator != null) {

                cancelGeneratorForTerminal(
                        activeGenerator,
                        ExecutionStatus.CANCELLED,
                        conversationId
                );
            }

            return true;
        }

        private void cancelGeneratorAfterFailure(
                UUID conversationId
        ) {

            var activeGenerator = generator;

            if (activeGenerator == null) {
                return;
            }

            try {
                activeGenerator.cancel(true);

            } catch (RuntimeException exception) {

                log.debug(
                        "Generator cancellation failed during failure cleanup. Conversation: {}, Run: {}",
                        conversationId,
                        runId,
                        exception
                );
            }
        }

        private void shutdown(UUID conversationId) {

            AsyncGenerator.Cancellable<
                    NodeOutput<ErpAiGraphState>
            > activeGenerator;

            synchronized (lifecycleLock) {

                terminalState.compareAndSet(
                        ExecutionStatus.RUNNING,
                        ExecutionStatus.SHUTDOWN
                );

                activeGenerator =
                        terminalState.get() == ExecutionStatus.SHUTDOWN
                                ? generator
                                : null;
            }

            if (activeGenerator != null) {

                cancelGeneratorForTerminal(
                        activeGenerator,
                        ExecutionStatus.SHUTDOWN,
                        conversationId
                );
            }
        }

        private void cancelGeneratorForTerminal(
                AsyncGenerator.Cancellable<
                        NodeOutput<ErpAiGraphState>
                > activeGenerator,
                ExecutionStatus terminalStatus,
                UUID conversationId
        ) {

            try {

                boolean accepted = activeGenerator.cancel(true);

                if (!accepted) {

                    log.warn(
                            "Generator cancellation rejected after {}. Conversation: {}, Run: {}",
                            terminalStatus,
                            conversationId,
                            runId
                    );
                }

            } catch (RuntimeException exception) {

                log.warn(
                        "Generator cancellation failed after {}. Conversation: {}, Run: {}",
                        terminalStatus,
                        conversationId,
                        runId,
                        exception
                );
            }
        }

        private void scheduleTimeout(
                UUID conversationId,
                UUID runId,
                Duration executionTimeout,
                ScheduledExecutorService scheduler
        ) {

            try {

                timeoutTask = scheduler.schedule(() -> {

                    AsyncGenerator.Cancellable<
                            NodeOutput<ErpAiGraphState>
                    > activeGenerator;

                    synchronized (lifecycleLock) {

                        if (!terminalState.compareAndSet(
                                ExecutionStatus.RUNNING,
                                ExecutionStatus.TIMED_OUT
                        )) {
                            return;
                        }

                        activeGenerator = generator;
                    }

                    log.warn(
                            "AI execution timeout reached. Conversation: {}, Run: {}",
                            conversationId,
                            runId
                    );

                    if (activeGenerator != null) {

                        cancelGeneratorForTerminal(
                                activeGenerator,
                                ExecutionStatus.TIMED_OUT,
                                conversationId
                        );
                    }

                }, executionTimeout.toMillis(), TimeUnit.MILLISECONDS);

            } catch (RejectedExecutionException exception) {

                throw new IllegalStateException(
                        "AI agent timeout scheduler is unavailable",
                        exception
                );
            }
        }
    }

    // --------------------------------------------------
    // SCHEDULER
    // --------------------------------------------------

    private static ScheduledExecutorService newTimeoutScheduler() {

        return Executors.newSingleThreadScheduledExecutor(task -> {

            Thread thread = new Thread(
                    task,
                    "ai-agent-timeout"
            );

            thread.setDaemon(true);

            return thread;
        });
    }

    // --------------------------------------------------
    // APPLICATION SHUTDOWN
    // --------------------------------------------------

    @PreDestroy
    public void shutdownTimeoutScheduler() {

        List<Map.Entry<UUID, ActiveExecution>> executions;

        synchronized (registryLock) {

            if (!shutdown.compareAndSet(false, true)) {
                return;
            }

            executions = List.copyOf(
                    activeExecutions.entrySet()
            );
        }

        for (Map.Entry<UUID, ActiveExecution> entry : executions) {

            ActiveExecution execution = entry.getValue();

            execution.shutdown(entry.getKey());

            activeExecutions.remove(
                    entry.getKey(),
                    execution
            );

            if (execution.timeoutTask != null) {
                execution.timeoutTask.cancel(false);
            }
        }

        timeoutScheduler.shutdown();

        try {

            if (!timeoutScheduler.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            )) {
                timeoutScheduler.shutdownNow();
            }

        } catch (InterruptedException exception) {

            timeoutScheduler.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }
}
