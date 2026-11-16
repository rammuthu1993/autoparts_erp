package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

@Component
public class AiPerformanceMetrics {

    private static final Logger log =
            LoggerFactory.getLogger(AiPerformanceMetrics.class);

    private final ThreadLocal<ExecutionMetrics> currentExecution =
            new ThreadLocal<>();

    public ExecutionMetrics createExecution(
            UUID conversationId,
            UUID runId,
            long submittedAtNanos
    ) {
        return new ExecutionMetrics(conversationId, runId, submittedAtNanos);
    }

    public Scope activate(ExecutionMetrics execution) {
        ExecutionMetrics previous = currentExecution.get();
        currentExecution.set(execution);
        return () -> {
            if (previous == null) {
                currentExecution.remove();
            } else {
                currentExecution.set(previous);
            }
        };
    }

    public void recordQueueWait(
            ExecutionMetrics execution,
            long queueWaitNanos,
            long startedAtNanos
    ) {
        execution.recordQueueWait(queueWaitNanos, startedAtNanos);
    }

    public void recordModelCall(
            long durationNanos,
            ChatClientResponse response,
            Throwable failure
    ) {
        ExecutionMetrics execution = currentExecution.get();
        if (execution == null) {
            return;
        }

        ChatResponse chatResponse = response == null
                ? null
                : response.chatResponse();
        boolean toolCall = chatResponse != null && chatResponse.hasToolCalls();
        String model = chatResponse == null
                ? null
                : chatResponse.getMetadata().getModel();
        if (model != null && model.isBlank()) {
            model = null;
        }
        Usage usage = chatResponse == null
                ? null
                : chatResponse.getMetadata().getUsage();
        Integer promptTokens = usage == null ? null : usage.getPromptTokens();
        Integer completionTokens =
                usage == null ? null : usage.getCompletionTokens();
        String status = failure != null
                ? "FAILED"
                : response == null || chatResponse == null
                        ? "EMPTY_RESPONSE"
                        : "COMPLETED";
        int callNumber = execution.recordModelCall(
                durationNanos,
                model,
                promptTokens,
                completionTokens,
                !toolCall,
                status);

        log.info(
                "AI model call completed. conversationId={}, runId={}, callNumber={}, "
                        + "durationMs={}, finalResponseGeneration={}, promptTokens={}, "
                        + "completionTokens={}, model={}, executionStatus={}",
                execution.conversationId(),
                execution.runId(),
                callNumber,
                toMillis(durationNanos),
                !toolCall,
                promptTokens,
                completionTokens,
                model,
                status);
    }

    public <T> T measureTool(String toolName, Supplier<T> operation) {
        return measure("tool", toolName, operation);
    }

    public <T> T measureDatabaseQuery(String operation, Supplier<T> query) {
        return measure("databaseQuery", operation, query);
    }

    public void logExecutionSummary(
            ExecutionMetrics execution,
            String status,
            long finishedAtNanos
    ) {
        if (!execution.markSummaryLogged()) {
            return;
        }

        long totalElapsedNanos =
                Math.max(0, finishedAtNanos - execution.submittedAtNanos());
        long executionDurationNanos = execution.executionStartedAtNanos() == 0
                ? 0
                : Math.max(0, finishedAtNanos - execution.executionStartedAtNanos());

        log.info(
                "AI execution performance summary. conversationId={}, runId={}, "
                        + "queueWaitMs={}, totalElapsedMs={}, agentExecutionMs={}, "
                        + "modelCallCount={}, totalModelCallMs={}, finalResponseGenerationMs={}, "
                        + "promptTokens={}, completionTokens={}, model={}, toolCount={}, "
                        + "totalToolExecutionMs={}, tools={}, databaseQueryCount={}, "
                        + "totalDatabaseQueryMs={}, databaseQueries={}, executionStatus={}",
                execution.conversationId(),
                execution.runId(),
                toMillis(execution.queueWaitNanos()),
                toMillis(totalElapsedNanos),
                toMillis(executionDurationNanos),
                execution.modelCallCount(),
                toMillis(execution.totalModelCallNanos()),
                execution.finalResponseGenerationNanos() == null
                        ? null
                        : toMillis(execution.finalResponseGenerationNanos()),
                execution.promptTokens(),
                execution.completionTokens(),
                execution.modelName(),
                execution.toolCount(),
                toMillis(execution.totalToolExecutionNanos()),
                execution.toolNames(),
                execution.databaseQueryCount(),
                toMillis(execution.totalDatabaseQueryNanos()),
                execution.databaseQueryNames(),
                status);
    }

    private <T> T measure(
            String metricType,
            String operation,
            Supplier<T> action
    ) {
        ExecutionMetrics execution = currentExecution.get();
        long startedAtNanos = System.nanoTime();
        String status = "COMPLETED";

        try {
            return action.get();
        } catch (CancellationException exception) {
            status = "CANCELLED";
            throw exception;
        } catch (RuntimeException | Error exception) {
            status = "FAILED";
            throw exception;
        } finally {
            long durationNanos = System.nanoTime() - startedAtNanos;
            if (execution != null) {
                if ("tool".equals(metricType)) {
                    execution.recordTool(operation, durationNanos);
                } else {
                    execution.recordDatabaseQuery(operation, durationNanos);
                }
                log.info(
                        "AI {} timing. conversationId={}, runId={}, operation={}, "
                                + "durationMs={}, executionStatus={}",
                        metricType,
                        execution.conversationId(),
                        execution.runId(),
                        operation,
                        toMillis(durationNanos),
                        status);
            }
        }
    }

    private static long toMillis(long durationNanos) {
        return durationNanos / 1_000_000;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static final class ExecutionMetrics {

        private final UUID conversationId;
        private final UUID runId;
        private final long submittedAtNanos;
        private final AtomicBoolean summaryLogged = new AtomicBoolean();
        private final List<String> toolNames = new ArrayList<>();
        private final List<String> databaseQueryNames = new ArrayList<>();
        private long queueWaitNanos;
        private long executionStartedAtNanos;
        private long modelCallCount;
        private long totalModelCallNanos;
        private long totalToolExecutionNanos;
        private long totalDatabaseQueryNanos;
        private long promptTokens;
        private long completionTokens;
        private boolean tokenUsageAvailable;
        private Long finalResponseGenerationNanos;
        private String modelName;

        private ExecutionMetrics(
                UUID conversationId,
                UUID runId,
                long submittedAtNanos
        ) {
            this.conversationId = conversationId;
            this.runId = runId;
            this.submittedAtNanos = submittedAtNanos;
        }

        private synchronized int recordModelCall(
                long durationNanos,
                String modelName,
                Integer promptTokens,
                Integer completionTokens,
                boolean finalResponseGeneration,
                String status
        ) {
            this.modelCallCount++;
            this.totalModelCallNanos += durationNanos;
            if (modelName != null && !modelName.isBlank()) {
                this.modelName = modelName;
            }
            if (promptTokens != null || completionTokens != null) {
                this.tokenUsageAvailable = true;
                this.promptTokens += promptTokens == null ? 0 : promptTokens;
                this.completionTokens +=
                        completionTokens == null ? 0 : completionTokens;
            }
            if (finalResponseGeneration && !"FAILED".equals(status)) {
                this.finalResponseGenerationNanos = durationNanos;
            }
            return Math.toIntExact(this.modelCallCount);
        }

        private synchronized void recordTool(
                String toolName,
                long durationNanos
        ) {
            this.toolNames.add(toolName);
            this.totalToolExecutionNanos += durationNanos;
        }

        private synchronized void recordDatabaseQuery(
                String operation,
                long durationNanos
        ) {
            this.databaseQueryNames.add(operation);
            this.totalDatabaseQueryNanos += durationNanos;
        }

        private synchronized void recordQueueWait(
                long queueWaitNanos,
                long startedAtNanos
        ) {
            this.queueWaitNanos = Math.max(0, queueWaitNanos);
            this.executionStartedAtNanos = startedAtNanos;
        }

        private boolean markSummaryLogged() {
            return this.summaryLogged.compareAndSet(false, true);
        }

        private UUID conversationId() {
            return this.conversationId;
        }

        private UUID runId() {
            return this.runId;
        }

        private long submittedAtNanos() {
            return this.submittedAtNanos;
        }

        private long queueWaitNanos() {
            return this.queueWaitNanos;
        }

        private long executionStartedAtNanos() {
            return this.executionStartedAtNanos;
        }

        synchronized long modelCallCount() {
            return this.modelCallCount;
        }

        synchronized long totalModelCallNanos() {
            return this.totalModelCallNanos;
        }

        synchronized Long finalResponseGenerationNanos() {
            return this.finalResponseGenerationNanos;
        }

        synchronized Long promptTokens() {
            return this.tokenUsageAvailable ? this.promptTokens : null;
        }

        synchronized Long completionTokens() {
            return this.tokenUsageAvailable ? this.completionTokens : null;
        }

        synchronized String modelName() {
            return this.modelName;
        }

        synchronized long toolCount() {
            return this.toolNames.size();
        }

        synchronized long totalToolExecutionNanos() {
            return this.totalToolExecutionNanos;
        }

        synchronized List<String> toolNames() {
            return List.copyOf(this.toolNames);
        }

        synchronized long databaseQueryCount() {
            return this.databaseQueryNames.size();
        }

        synchronized long totalDatabaseQueryNanos() {
            return this.totalDatabaseQueryNanos;
        }

        synchronized List<String> databaseQueryNames() {
            return List.copyOf(this.databaseQueryNames);
        }
    }
}
