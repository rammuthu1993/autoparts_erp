package com.autoparts.erp.ai.service;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;
import com.autoparts.erp.ai.exception.AiExecutionInProgressException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpAiAgentServiceConcurrencyTest {

    private static final long WAIT_SECONDS = 5;

    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<ErpAiAgentService> services = new ArrayList<>();
    private final List<ScheduledThreadPoolExecutor> schedulers = new ArrayList<>();

    @AfterEach
    void cleanUp() throws InterruptedException {
        services.forEach(ErpAiAgentService::shutdownTimeoutScheduler);
        workers.shutdownNow();
        workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        schedulers.forEach(ScheduledThreadPoolExecutor::shutdownNow);
    }

    @Test
    void normalCompletionWinsAndRemovesRegistryEntry() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        var generator = generator("answer");
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        CountDownLatch graphStarted = new CountDownLatch(1);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    graphStarted.countDown();
                    return generator;
                });

        Future<String> chat = chatAsync(harness, conversationId);

        await(graphStarted);
        assertEquals("answer", chat.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                ErpAiAgentService.ExecutionStatus.COMPLETED);
        assertTrue(harness.service.cancel(conversationId).isEmpty());
    }

    @Test
    void timeoutWinsBeforeCompletion() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        CountDownLatch generatorCancelled = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        doAnswer(invocation -> {
            generatorCancelled.countDown();
            return true;
        }).when(generator).cancel(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        fireTimeout(harness);
        await(generatorCancelled);
        assertEquals(ErpAiAgentService.ExecutionStatus.TIMED_OUT, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, AiAgentExecutionTimeoutException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.TIMED_OUT);
    }

    @Test
    void cancellationWinsBeforeCompletion() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenReturn(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        assertEquals(Optional.of(getRunId(harness.service, conversationId)),
                harness.service.cancel(conversationId));
        assertEquals(ErpAiAgentService.ExecutionStatus.CANCELLED, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, CancellationException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.CANCELLED);
    }

    @Test
    void completionWinsBeforeCancellation() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        var generator = generator("answer");
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        CountDownLatch graphStarted = new CountDownLatch(1);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    graphStarted.countDown();
                    return generator;
                });

        assertEquals("answer", chatAsync(harness, conversationId)
                .get(WAIT_SECONDS, TimeUnit.SECONDS));
        await(graphStarted);
        assertTrue(harness.service.cancel(conversationId).isEmpty());
        assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                ErpAiAgentService.ExecutionStatus.COMPLETED);
    }

    @Test
    void timeoutWinsBeforeCancellation() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        CountDownLatch generatorCancelled = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        doAnswer(invocation -> {
            generatorCancelled.countDown();
            return true;
        }).when(generator).cancel(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);
        fireTimeout(harness);
        await(generatorCancelled);

        assertTrue(harness.service.cancel(conversationId).isEmpty());
        assertEquals(ErpAiAgentService.ExecutionStatus.TIMED_OUT, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, AiAgentExecutionTimeoutException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.TIMED_OUT);
    }

    @Test
    void cancellationWinsBeforeTimeout() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenReturn(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);
        UUID runId = getRunId(harness.service, conversationId);

        assertEquals(Optional.of(runId), harness.service.cancel(conversationId));
        fireTimeout(harness);
        assertEquals(ErpAiAgentService.ExecutionStatus.CANCELLED, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, CancellationException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.CANCELLED);
    }

    @Test
    void cancellationDuringGeneratorInitializationIsNotLost() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch initializationEntered = new CountDownLatch(1);
        CountDownLatch finishInitialization = new CountDownLatch(1);
        CountDownLatch generatorCancelled = new CountDownLatch(1);
        CountDownLatch cancellationReturned = new CountDownLatch(1);
        UUID runId = UUID.randomUUID();
        var generator = generator("answer");
        doAnswer(invocation -> {
            generatorCancelled.countDown();
            return true;
        }).when(generator).cancel(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    initializationEntered.countDown();
                    await(finishInitialization);
                    return generator;
                });

        Future<String> chat = chatAsync(harness, conversationId, runId);
        await(initializationEntered);
        var status = executionStatus(harness.service, conversationId);
        Future<Optional<UUID>> cancellation = workers.submit(() -> {
            Optional<UUID> accepted = harness.service.cancel(conversationId);
            cancellationReturned.countDown();
            return accepted;
        });
        await(cancellationReturned);
        assertEquals(Optional.of(runId), cancellation.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(ErpAiAgentService.ExecutionStatus.CANCELLED, status.get());

        finishInitialization.countDown();
        await(generatorCancelled);
        assertFutureFailure(chat, CancellationException.class);
        verify(generator, never()).stream();
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.CANCELLED);
    }

    @Test
    void timeoutDuringGeneratorInitializationCancelsLateGenerator() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch initializationEntered = new CountDownLatch(1);
        CountDownLatch finishInitialization = new CountDownLatch(1);
        CountDownLatch generatorCancelled = new CountDownLatch(1);
        var generator = generator("answer");
        doAnswer(invocation -> {
            generatorCancelled.countDown();
            return true;
        }).when(generator).cancel(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    initializationEntered.countDown();
                    await(finishInitialization);
                    return generator;
                });

        Future<String> chat = chatAsync(harness, conversationId);
        await(initializationEntered);
        var status = executionStatus(harness.service, conversationId);

        fireTimeout(harness);
        assertEquals(ErpAiAgentService.ExecutionStatus.TIMED_OUT, status.get());
        assertEquals(1, generatorCancelled.getCount());

        finishInitialization.countDown();
        await(generatorCancelled);
        assertFutureFailure(chat, AiAgentExecutionTimeoutException.class);
        verify(generator, never()).stream();
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.TIMED_OUT);
    }

    @Test
    void timeoutCancellationReturningFalsePreservesTimedOutState() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenReturn(false);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        fireTimeout(harness);
        assertEquals(ErpAiAgentService.ExecutionStatus.TIMED_OUT, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, AiAgentExecutionTimeoutException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.TIMED_OUT);
    }

    @Test
    void timeoutCancellationThrowingPreservesTimedOutState() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenThrow(new IllegalStateException("cancel failed"));
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        fireTimeout(harness);
        assertEquals(ErpAiAgentService.ExecutionStatus.TIMED_OUT, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, AiAgentExecutionTimeoutException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.TIMED_OUT);
    }

    @Test
    void concurrentTimeoutAndCancellationHaveOneTerminalWinner() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        CountDownLatch contendersReady = new CountDownLatch(2);
        CountDownLatch startContenders = new CountDownLatch(1);
        AtomicInteger cancellationCalls = new AtomicInteger();
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        doAnswer(invocation -> {
            cancellationCalls.incrementAndGet();
            return true;
        }).when(generator).cancel(true);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);
        Future<?> timeout = workers.submit(() -> {
            contendersReady.countDown();
            await(startContenders);
            fireTimeout(harness);
        });
        Future<Optional<UUID>> cancellation = workers.submit(() -> {
            contendersReady.countDown();
            await(startContenders);
            return harness.service.cancel(conversationId);
        });
        await(contendersReady);
        startContenders.countDown();
        timeout.get(WAIT_SECONDS, TimeUnit.SECONDS);
        Optional<UUID> acceptedCancellation =
                cancellation.get(WAIT_SECONDS, TimeUnit.SECONDS);

        ErpAiAgentService.ExecutionStatus winner = status.get();
        assertTrue(winner == ErpAiAgentService.ExecutionStatus.TIMED_OUT
                || winner == ErpAiAgentService.ExecutionStatus.CANCELLED);
        assertEquals(
                winner == ErpAiAgentService.ExecutionStatus.CANCELLED,
                acceptedCancellation.isPresent()
        );
        assertEquals(1, cancellationCalls.get());
        releaseStream.countDown();
        assertFutureFailure(
                chat,
                winner == ErpAiAgentService.ExecutionStatus.TIMED_OUT
                        ? AiAgentExecutionTimeoutException.class
                        : CancellationException.class
        );
        assertTerminalAndRemoved(harness.service, conversationId, status, winner);
    }

    @Test
    void completionRacesCancellationAndOnlyOneOutcomeIsObserved() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        CountDownLatch contendersReady = new CountDownLatch(2);
        CountDownLatch startContenders = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenReturn(true);
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    return generator;
                });

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        Future<Optional<UUID>> cancellation = workers.submit(() -> {
            contendersReady.countDown();
            await(startContenders);
            return harness.service.cancel(conversationId);
        });
        Future<?> completion = workers.submit(() -> {
            contendersReady.countDown();
            await(startContenders);
            releaseStream.countDown();
        });
        await(contendersReady);
        startContenders.countDown();
        completion.get(WAIT_SECONDS, TimeUnit.SECONDS);
        Optional<UUID> acceptedCancellation =
                cancellation.get(WAIT_SECONDS, TimeUnit.SECONDS);

        if (acceptedCancellation.isPresent()) {
            assertFutureFailure(chat, CancellationException.class);
            assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                    ErpAiAgentService.ExecutionStatus.CANCELLED);
        } else {
            assertEquals("answer", chat.get(WAIT_SECONDS, TimeUnit.SECONDS));
            assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                    ErpAiAgentService.ExecutionStatus.COMPLETED);
        }
    }

    @Test
    void cancellationRejectionDoesNotUndoAcceptedCancellation() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenReturn(false);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        assertEquals(Optional.of(getRunId(harness.service, conversationId)),
                harness.service.cancel(conversationId));
        assertEquals(ErpAiAgentService.ExecutionStatus.CANCELLED, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, CancellationException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.CANCELLED);
    }

    @Test
    void cancellationExceptionDoesNotUndoAcceptedCancellation() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(generator.cancel(true)).thenThrow(new IllegalStateException("cancel failed"));
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        assertTrue(harness.service.cancel(conversationId).isPresent());
        assertEquals(ErpAiAgentService.ExecutionStatus.CANCELLED, status.get());
        releaseStream.countDown();
        assertFutureFailure(chat, CancellationException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status,
                ErpAiAgentService.ExecutionStatus.CANCELLED);
    }

    @Test
    void shutdownIsDistinctFromUserCancellation() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenReturn(generator);

        Future<String> chat = chatAsync(harness, conversationId);
        await(streamEntered);
        var status = executionStatus(harness.service, conversationId);

        harness.service.shutdownTimeoutScheduler();
        assertEquals(ErpAiAgentService.ExecutionStatus.SHUTDOWN, status.get());
        assertFalse(isRegistered(harness.service, conversationId));
        releaseStream.countDown();
        assertFutureFailure(chat, IllegalStateException.class);
        assertEquals(ErpAiAgentService.ExecutionStatus.SHUTDOWN, status.get());
    }

    @Test
    void generatorCreatedAfterShutdownIsCancelledImmediately() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch initializationEntered = new CountDownLatch(1);
        CountDownLatch finishInitialization = new CountDownLatch(1);
        CountDownLatch generatorCancelled = new CountDownLatch(1);
        var generator = generator("answer");
        doAnswer(invocation -> {
            generatorCancelled.countDown();
            return true;
        }).when(generator).cancel(true);
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    initializationEntered.countDown();
                    await(finishInitialization);
                    return generator;
                });

        Future<String> chat = chatAsync(harness, conversationId);
        await(initializationEntered);
        harness.service.shutdownTimeoutScheduler();
        assertEquals(ErpAiAgentService.ExecutionStatus.SHUTDOWN, status.get().get());
        assertFalse(isRegistered(harness.service, conversationId));
        finishInitialization.countDown();
        await(generatorCancelled);

        assertFutureFailure(chat, IllegalStateException.class);
        verify(generator).cancel(true);
        assertEquals(ErpAiAgentService.ExecutionStatus.SHUTDOWN, status.get().get());
    }

    @Test
    void schedulerRejectionCleansUpAndMarksFailure() throws Exception {
        UUID conversationId = UUID.randomUUID();
        AtomicReference<Harness> harnessReference = new AtomicReference<>();
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        var rejectingScheduler = new ScheduledThreadPoolExecutor(1) {
            @Override
            public ScheduledFuture<?> schedule(
                    Runnable command,
                    long delay,
                    TimeUnit unit) {
                try {
                    status.set(executionStatus(
                            harnessReference.get().service,
                            conversationId
                    ));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                throw new java.util.concurrent.RejectedExecutionException("closed");
            }
        };
        Harness harness = harness(Duration.ofSeconds(30), rejectingScheduler);
        harnessReference.set(harness);

        assertFutureFailure(chatAsync(harness, conversationId), IllegalStateException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                ErpAiAgentService.ExecutionStatus.FAILED);
    }

    @Test
    void graphInitializationFailureCleansUpAndMarksFailure() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch initializationEntered = new CountDownLatch(1);
        CountDownLatch failInitialization = new CountDownLatch(1);
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    initializationEntered.countDown();
                    await(failInitialization);
                    throw new IllegalStateException("graph initialization failed");
                });

        Future<String> chat = chatAsync(harness, conversationId);
        await(initializationEntered);
        failInitialization.countDown();
        assertFutureFailure(chat, IllegalStateException.class);
        assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                ErpAiAgentService.ExecutionStatus.FAILED);
    }

    @Test
    void graphExecutionFailureIsPreservedWhenCleanupCancellationFails() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        var generator = mockGenerator();
        var providerFailure = new IllegalStateException("provider failed");
        when(generator.stream()).thenThrow(providerFailure);
        when(generator.cancel(true)).thenThrow(
                new IllegalStateException("cleanup cancellation failed")
        );
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    return generator;
                });

        assertSame(
                providerFailure,
                assertFutureFailure(
                        chatAsync(harness, conversationId),
                        IllegalStateException.class
                )
        );
        assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                ErpAiAgentService.ExecutionStatus.FAILED);
    }

    @Test
    void emptyAssistantResponseFailsAndCleansUp() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        var generator = generator(" ");
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    return generator;
                });

        assertFutureFailure(chatAsync(harness, conversationId), IllegalStateException.class);
        assertTerminalAndRemoved(harness.service, conversationId,
                status.get(), ErpAiAgentService.ExecutionStatus.FAILED);
    }

    @Test
    void simultaneousSameConversationExecutionIsRejected() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID conversationId = UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        var generator = generatorBlocking("answer", streamEntered, releaseStream);
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> status =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    status.set(executionStatus(harness.service, conversationId));
                    return generator;
                });

        Future<String> first = chatAsync(harness, conversationId);
        await(streamEntered);
        assertThrows(
                AiExecutionInProgressException.class,
                () -> harness.service.chat(
                        conversationId,
                        UUID.randomUUID(),
                        List.of(new UserMessage("request"))
                )
        );
        releaseStream.countDown();
        assertEquals("answer", first.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTerminalAndRemoved(harness.service, conversationId, status.get(),
                ErpAiAgentService.ExecutionStatus.COMPLETED);
    }

    @Test
    void differentConversationsExecuteIndependently() throws Exception {
        Harness harness = harness(Duration.ofSeconds(30));
        UUID firstConversation = UUID.randomUUID();
        UUID secondConversation = UUID.randomUUID();
        CountDownLatch bothStreamsEntered = new CountDownLatch(2);
        CountDownLatch releaseStreams = new CountDownLatch(1);
        var firstGenerator = generatorBlocking("first", bothStreamsEntered, releaseStreams);
        var secondGenerator = generatorBlocking("second", bothStreamsEntered, releaseStreams);
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> firstStatus =
                new AtomicReference<>();
        AtomicReference<AtomicReference<ErpAiAgentService.ExecutionStatus>> secondStatus =
                new AtomicReference<>();
        when(harness.graph.stream(any(GraphInput.class), any(RunnableConfig.class)))
                .thenAnswer(invocation -> {
                    String threadId = invocation.getArgument(1, RunnableConfig.class)
                            .threadId()
                            .orElseThrow();
                    if (threadId.equals(firstConversation.toString())) {
                        firstStatus.set(executionStatus(harness.service, firstConversation));
                        return firstGenerator;
                    }
                    secondStatus.set(executionStatus(harness.service, secondConversation));
                    return secondGenerator;
                });

        Future<String> first = chatAsync(harness, firstConversation);
        Future<String> second = chatAsync(harness, secondConversation);
        await(bothStreamsEntered);
        assertTrue(isRegistered(harness.service, firstConversation));
        assertTrue(isRegistered(harness.service, secondConversation));
        releaseStreams.countDown();

        assertEquals("first", first.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("second", second.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTerminalAndRemoved(harness.service, firstConversation, firstStatus.get(),
                ErpAiAgentService.ExecutionStatus.COMPLETED);
        assertTerminalAndRemoved(harness.service, secondConversation, secondStatus.get(),
                ErpAiAgentService.ExecutionStatus.COMPLETED);
    }

    private Harness harness(Duration timeout) {
        return harness(timeout, new ManualScheduler());
    }

    private Harness harness(
            Duration timeout,
            ScheduledThreadPoolExecutor scheduler) {
        ErpAiGraphWorkflow graph =
                mock(ErpAiGraphWorkflow.class);

        ErpAiAgentService service =
                new ErpAiAgentService(graph, timeout, scheduler);

        services.add(service);
        schedulers.add(scheduler);

        return new Harness(graph, service, scheduler);
    }

    private Future<String> chatAsync(Harness harness, UUID conversationId) {
        return chatAsync(harness, conversationId, UUID.randomUUID());
    }

    private Future<String> chatAsync(
            Harness harness,
            UUID conversationId,
            UUID runId) {
        return workers.submit(() -> harness.service.chat(
                conversationId,
                runId,
                List.of(new UserMessage("request"))
        ));
    }

    private static void fireTimeout(Harness harness) {
        assertInstanceOf(ManualScheduler.class, harness.scheduler);
        ((ManualScheduler) harness.scheduler).fireTimeout();
    }

    private static AsyncGenerator.Cancellable<NodeOutput<ErpAiGraphState>> generator(
            String response) {
        var generator = mockGenerator();
        when(generator.stream()).thenReturn(Stream.of(output(response)));
        return generator;
    }

    private static AsyncGenerator.Cancellable<NodeOutput<ErpAiGraphState>> generatorBlocking(
            String response,
            CountDownLatch streamEntered,
            CountDownLatch releaseStream) {
        var generator = mockGenerator();
        when(generator.stream()).thenAnswer(invocation -> {
            streamEntered.countDown();
            await(releaseStream);
            return Stream.of(output(response));
        });
        return generator;
    }

    @SuppressWarnings("unchecked")
    private static AsyncGenerator.Cancellable<NodeOutput<ErpAiGraphState>> mockGenerator() {
        return mock(AsyncGenerator.Cancellable.class);
    }

    private static NodeOutput<ErpAiGraphState> output(String response) {
        var state = new ErpAiGraphState(Map.of("finalResponse", response));
        return new NodeOutput<>("agent", state);
    }

    private static AtomicReference<ErpAiAgentService.ExecutionStatus> executionStatus(
            ErpAiAgentService service,
            UUID conversationId) throws Exception {
        Object execution = execution(service, conversationId);
        Field terminalField = execution.getClass().getDeclaredField("terminalState");
        terminalField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<ErpAiAgentService.ExecutionStatus> state =
                (AtomicReference<ErpAiAgentService.ExecutionStatus>) terminalField.get(execution);
        return state;
    }

    private static UUID getRunId(
            ErpAiAgentService service,
            UUID conversationId) throws Exception {
        Object execution = execution(service, conversationId);
        Field runIdField = execution.getClass().getDeclaredField("runId");
        runIdField.setAccessible(true);
        return (UUID) runIdField.get(execution);
    }

    private static Object execution(
            ErpAiAgentService service,
            UUID conversationId) throws Exception {
        Field executionsField = ErpAiAgentService.class.getDeclaredField("activeExecutions");
        executionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> executions = (Map<UUID, Object>) executionsField.get(service);
        Object execution = executions.get(conversationId);
        assertTrue(execution != null, "Expected active execution to be registered");
        return execution;
    }

    private static boolean isRegistered(
            ErpAiAgentService service,
            UUID conversationId) throws Exception {
        Field executionsField = ErpAiAgentService.class.getDeclaredField("activeExecutions");
        executionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> executions = (Map<UUID, Object>) executionsField.get(service);
        return executions.containsKey(conversationId);
    }

    private static void assertTerminalAndRemoved(
            ErpAiAgentService service,
            UUID conversationId,
            AtomicReference<ErpAiAgentService.ExecutionStatus> status,
            ErpAiAgentService.ExecutionStatus expected) throws Exception {
        assertEquals(expected, status.get());
        assertFalse(isRegistered(service, conversationId));
    }

    private static Throwable assertFutureFailure(
            Future<?> future,
            Class<? extends Throwable> expectedType) throws Exception {
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS)
        );
        return assertInstanceOf(expectedType, failure.getCause());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(WAIT_SECONDS, TimeUnit.SECONDS),
                    "Timed out waiting for coordination latch");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test latch", exception);
        }
    }

    private record Harness(
            ErpAiGraphWorkflow graph,
            ErpAiAgentService service,
            ScheduledThreadPoolExecutor scheduler) {
    }

    private static class ManualScheduler extends ScheduledThreadPoolExecutor {

        private volatile Runnable timeoutTask;
        private final ScheduledFuture<?> timeoutFuture = mock(ScheduledFuture.class);

        private ManualScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(
                Runnable command,
                long delay,
                TimeUnit unit) {
            timeoutTask = command;
            return timeoutFuture;
        }

        private void fireTimeout() {
            Runnable task = timeoutTask;
            assertNotNull(task, "Expected timeout to be scheduled");
            task.run();
        }
    }
}
