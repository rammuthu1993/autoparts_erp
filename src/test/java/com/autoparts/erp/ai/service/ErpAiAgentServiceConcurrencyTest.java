package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;
import com.autoparts.erp.ai.exception.AiExecutionInProgressException;
import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.tools.ErpAiTools;

class ErpAiAgentServiceConcurrencyTest {

    private final ExecutorService callers = Executors.newCachedThreadPool();
    private final List<ErpAiAgentService> services = new ArrayList<>();

    @AfterEach
    void cleanUp() throws InterruptedException {
        services.forEach(ErpAiAgentService::shutdown);
        callers.shutdownNow();
        assertTrue(callers.awaitTermination(5, TimeUnit.SECONDS));
        SecurityContextHolder.clearContext();
    }

    @Test
    void completesChatAndAllowsASecondExecutionForTheConversation() {
        ErpAiAgentService service = createService(
                Duration.ofSeconds(5),
                2,
                invocation -> response("answer")
        );
        UUID conversationId = UUID.randomUUID();

        assertEquals(
                "answer",
                service.chat(conversationId, UUID.randomUUID(), messages("first"))
        );
        assertTrue(service.cancel(conversationId).isEmpty());
        assertEquals(
                "answer",
                service.chat(conversationId, UUID.randomUUID(), messages("second"))
        );
    }

    @Test
    void rejectsConcurrentExecutionForTheSameConversation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ErpAiAgentService service = createBlockingService(
                entered,
                release,
                Duration.ofSeconds(30),
                2
        );
        UUID conversationId = UUID.randomUUID();

        Future<String> first = callers.submit(() -> service.chat(
                conversationId,
                UUID.randomUUID(),
                messages("first")
        ));

        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertThrows(
                    AiExecutionInProgressException.class,
                    () -> service.chat(
                            conversationId,
                            UUID.randomUUID(),
                            messages("second")
                    )
            );
            release.countDown();
            assertEquals("answer", first.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void cancellationInterruptsExecutionAndAllowsAReplacementRequest()
            throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger();
        ErpAiAgentService service = createService(
                Duration.ofSeconds(30),
                1,
                invocation -> {
                    if (callCount.getAndIncrement() == 0) {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new CancellationException(
                                    "mock provider call interrupted");
                        } finally {
                            exited.countDown();
                        }
                    }
                    return response("answer");
                }
        );
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        Future<String> request = callers.submit(() -> service.chat(
                conversationId,
                runId,
                messages("cancel me")
        ));

        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals(
                    java.util.Optional.of(runId),
                    service.cancel(conversationId)
            );

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> request.get(10, TimeUnit.SECONDS)
            );
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertTrue(exited.await(10, TimeUnit.SECONDS));

            assertEquals(
                    "answer",
                    retryWhenAvailable(service, conversationId, messages("retry"))
            );
        } finally {
            release.countDown();
        }
    }

    @Test
    void cancellationOfQueuedExecutionRemovesItsConversationReservation()
            throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger providerCallCount = new AtomicInteger();
        ErpAiAgentService service = createService(
                Duration.ofSeconds(30),
                1,
                invocation -> {
                    if (providerCallCount.getAndIncrement() == 0) {
                        firstEntered.countDown();
                        try {
                            releaseFirst.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new CancellationException(
                                    "mock provider call interrupted");
                        }
                    }
                    return response("answer");
                }
        );
        UUID firstConversation = UUID.randomUUID();
        UUID queuedConversation = UUID.randomUUID();
        UUID queuedRun = UUID.randomUUID();

        Future<String> first = callers.submit(() -> service.chat(
                firstConversation,
                UUID.randomUUID(),
                messages("occupy worker")
        ));

        try {
            assertTrue(firstEntered.await(10, TimeUnit.SECONDS));
            Future<String> queued = callers.submit(() -> service.chat(
                    queuedConversation,
                    queuedRun,
                    messages("queued")
            ));

            assertEquals(
                    java.util.Optional.of(queuedRun),
                    cancelWhenAvailable(service, queuedConversation)
            );
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> queued.get(10, TimeUnit.SECONDS)
            );
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertEquals(1, providerCallCount.get());

            Future<String> retry = callers.submit(() -> service.chat(
                    queuedConversation,
                    UUID.randomUUID(),
                    messages("retry")
            ));
            releaseFirst.countDown();

            assertEquals("answer", first.get(10, TimeUnit.SECONDS));
            assertEquals("answer", retry.get(10, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void timesOutAnExecutionAndAllowsAReplacementRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger();
        ErpAiAgentService service = createService(
                Duration.ofSeconds(5),
                1,
                invocation -> {
                    if (callCount.getAndIncrement() == 0) {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new CancellationException(
                                    "mock provider call interrupted");
                        } finally {
                            exited.countDown();
                        }
                    }
                    return response("answer");
                }
        );
        UUID conversationId = UUID.randomUUID();

        Future<String> request = callers.submit(() -> service.chat(
                conversationId,
                UUID.randomUUID(),
                messages("slow")
        ));

        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> request.get(10, TimeUnit.SECONDS)
            );
            assertInstanceOf(
                    AiAgentExecutionTimeoutException.class,
                    failure.getCause()
            );
            assertTrue(exited.await(10, TimeUnit.SECONDS));

            assertEquals(
                    "answer",
                    retryWhenAvailable(service, conversationId, messages("retry"))
            );
        } finally {
            release.countDown();
        }
    }

    @Test
    void failedExecutionDoesNotBlockTheNextExecution() {
        AtomicInteger callCount = new AtomicInteger();
        ErpAiAgentService service = createService(
                Duration.ofSeconds(5),
                1,
                invocation -> {
                    if (callCount.getAndIncrement() == 0) {
                        throw new IllegalStateException("simulated provider failure");
                    }
                    return response("answer");
                }
        );
        UUID conversationId = UUID.randomUUID();

        assertThrows(
                IllegalStateException.class,
                () -> service.chat(
                        conversationId,
                        UUID.randomUUID(),
                        messages("fail")
                )
        );
        assertEquals(
                "answer",
                service.chat(conversationId, UUID.randomUUID(), messages("retry"))
        );
    }

    @Test
    void differentConversationsCanExecuteConcurrently() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ErpAiAgentService service = createBlockingService(
                entered,
                release,
                Duration.ofSeconds(30),
                2
        );

        Future<String> first = callers.submit(() -> service.chat(
                UUID.randomUUID(),
                UUID.randomUUID(),
                messages("first")
        ));
        Future<String> second = callers.submit(() -> service.chat(
                UUID.randomUUID(),
                UUID.randomUUID(),
                messages("second")
        ));

        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertEquals("answer", first.get(10, TimeUnit.SECONDS));
            assertEquals("answer", second.get(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void shutdownInterruptsRunningWorkAndRejectsFutureWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ErpAiAgentService service = createBlockingService(
                entered,
                release,
                Duration.ofSeconds(30),
                1
        );
        UUID conversationId = UUID.randomUUID();
        Future<String> request = callers.submit(() -> service.chat(
                conversationId,
                UUID.randomUUID(),
                messages("shutdown")
        ));

        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            service.shutdown();
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> request.get(10, TimeUnit.SECONDS)
            );
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertThrows(
                    IllegalStateException.class,
                    () -> service.chat(
                            conversationId,
                            UUID.randomUUID(),
                            messages("after shutdown")
                    )
            );
        } finally {
            release.countDown();
        }
    }

    @Test
    void emptyFinalResponseFailsExplicitlyAndDoesNotBlockRetry() {
        AtomicInteger callCount = new AtomicInteger();
        ErpAiAgentService service = createService(
                Duration.ofSeconds(5),
                1,
                invocation -> callCount.getAndIncrement() == 0
                        ? responseWithToolCall(null)
                        : response("answer")
        );
        UUID conversationId = UUID.randomUUID();

        assertThrows(
                AiProviderResponseException.class,
                () -> service.chat(
                        conversationId,
                        UUID.randomUUID(),
                        messages("compare recent purchases")
                )
        );
        assertEquals(
                "answer",
                service.chat(conversationId, UUID.randomUUID(), messages("retry"))
        );
    }

    @Test
    void extractsTextFromTheFirstNonemptyGeneration() {
        ErpAiAgentService service = createService(
                Duration.ofSeconds(5),
                1,
                invocation -> responseWithGenerations(null, "answer")
        );

        assertEquals(
                "answer",
                service.chat(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        messages("hello")
                )
        );
    }

    @Test
    void propagatesSecurityContextToWorkerAndClearsItAfterward() {
        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<Authentication> firstAuthentication =
                new AtomicReference<>();
        AtomicReference<Authentication> secondAuthentication =
                new AtomicReference<>();
        ErpAiAgentService service = createService(
                Duration.ofSeconds(5),
                1,
                invocation -> {
                    Authentication current = SecurityContextHolder.getContext()
                            .getAuthentication();
                    if (callCount.getAndIncrement() == 0) {
                        firstAuthentication.set(current);
                    } else {
                        secondAuthentication.set(current);
                    }
                    return response("answer");
                }
        );
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("test-user", "secret");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            assertEquals(
                    "answer",
                    service.chat(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            messages("first")
                    )
            );
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(
                "answer",
                service.chat(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        messages("second")
                )
        );
        assertSame(authentication, firstAuthentication.get());
        assertNull(secondAuthentication.get());
    }

    private String retryWhenAvailable(
            ErpAiAgentService service,
            UUID conversationId,
            List<Message> messages) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            try {
                return service.chat(conversationId, UUID.randomUUID(), messages);
            } catch (AiExecutionInProgressException exception) {
                if (System.nanoTime() >= deadline) {
                    throw exception;
                }
                Thread.yield();
            }
        }
    }

    private java.util.Optional<UUID> cancelWhenAvailable(
            ErpAiAgentService service,
            UUID conversationId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            java.util.Optional<UUID> runId = service.cancel(conversationId);
            if (runId.isPresent()) {
                return runId;
            }
            if (System.nanoTime() >= deadline) {
                return runId;
            }
            Thread.yield();
        }
    }

    private ErpAiAgentService createBlockingService(
            CountDownLatch entered,
            CountDownLatch release,
            Duration timeout,
            int maxConcurrentExecutions) {
        return createService(
                timeout,
                maxConcurrentExecutions,
                invocation -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException(
                                "mock provider call interrupted");
                    }
                    return response("answer");
                }
        );
    }

    private ErpAiAgentService createService(
            Duration timeout,
            int maxConcurrentExecutions,
            Answer<ChatClientResponse> responseAnswer) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(client);
        when(client.prompt()
                .system(anyString())
                .messages(anyList())
                .tools(any(Object[].class))
                .advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class))
                .call()
                .chatClientResponse())
                .thenAnswer(responseAnswer);

        ErpAiAgentService service = new ErpAiAgentService(
                builder,
                mock(ErpAiTools.class),
                new AiPerformanceMetrics(),
                timeout,
                maxConcurrentExecutions,
                8
        );
        services.add(service);
        return service;
    }

    private static ChatClientResponse response(String content) {
        return responseWithGenerations(content);
    }

    private static ChatClientResponse responseWithGenerations(String... contents) {
        List<Generation> generations = java.util.Arrays.stream(contents)
                .map(content -> generation(content, List.of(), "stop"))
                .toList();
        return new ChatClientResponse(
                new ChatResponse(generations),
                Map.of()
        );
    }

    private static ChatClientResponse responseWithToolCall(String content) {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "tool-call-id",
                "function",
                "searchPurchases",
                "{}"
        );
        return new ChatClientResponse(
                new ChatResponse(List.of(generation(
                        content,
                        List.of(toolCall),
                        "tool_calls"
                ))),
                Map.of()
        );
    }

    private static Generation generation(
            String content,
            List<AssistantMessage.ToolCall> toolCalls,
            String finishReason) {
        return new Generation(
                AssistantMessage.builder()
                        .content(content)
                        .toolCalls(toolCalls)
                        .build(),
                ChatGenerationMetadata.builder()
                        .finishReason(finishReason)
                        .build()
        );
    }

    private static List<Message> messages(String content) {
        return List.of(new UserMessage(content));
    }
}
