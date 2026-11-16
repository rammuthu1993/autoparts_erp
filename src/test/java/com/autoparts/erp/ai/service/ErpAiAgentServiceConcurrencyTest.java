package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;
import com.autoparts.erp.ai.exception.AiExecutionInProgressException;
import com.autoparts.erp.ai.tools.ErpAiTools;

class ErpAiAgentServiceConcurrencyTest {

    private final ExecutorService callers = Executors.newCachedThreadPool();
    private final ErpAiAgentService service = createService(Duration.ofSeconds(5));

    @AfterEach
    void cleanUp() {
        service.shutdown();
        callers.shutdownNow();
    }

    @Test
    void completesChatAndReturnsModelResponse() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        assertEquals("answer", service.chat(
                conversationId, runId, List.of(new org.springframework.ai.chat.messages.UserMessage("hello"))));
        assertTrue(service.cancel(conversationId).isEmpty());
    }

    @Test
    void rejectsConcurrentExecutionForSameConversation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ErpAiAgentService blockingService =
                createBlockingService(entered, release, Duration.ofSeconds(5));

        UUID conversationId = UUID.randomUUID();

        Future<String> first = callers.submit(() -> blockingService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new org.springframework.ai.chat.messages.UserMessage("first"))
        ));

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertThrows(
                    AiExecutionInProgressException.class,
                    () -> blockingService.chat(
                            conversationId,
                            UUID.randomUUID(),
                            List.of(new org.springframework.ai.chat.messages.UserMessage("second"))
                    )
            );

            // Allow the first request to complete before shutting down the service.
            release.countDown();

            assertEquals("answer", first.get(2, TimeUnit.SECONDS));

        } finally {
            // Ensure the blocking mock is released even if an assertion fails.
            release.countDown();
            blockingService.shutdown();
        }
    }

    @Test
    void cancellationReturnsRunIdAndInterruptsActiveRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(5);
        ErpAiAgentService blockingService = createBlockingService(entered, release, Duration.ofSeconds(5));
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        Future<String> request = callers.submit(() -> blockingService.chat(
                conversationId, runId,
                List.of(new org.springframework.ai.chat.messages.UserMessage("cancel me"))));

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(Optional.of(runId), blockingService.cancel(conversationId));
            ExecutionException failure = assertThrows(
                    ExecutionException.class, () -> request.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertTrue(blockingService.cancel(conversationId).isEmpty());
        } finally {
            release.countDown();
            blockingService.shutdown();
        }
    }

    @Test
    void timesOutAChatThatExceedsConfiguredDuration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(5);
        ErpAiAgentService blockingService = createBlockingService(entered, release, Duration.ofMillis(150));

        Future<String> request = callers.submit(() -> blockingService.chat(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new org.springframework.ai.chat.messages.UserMessage("slow"))));

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            ExecutionException failure = assertThrows(
                    ExecutionException.class, () -> request.get(2, TimeUnit.SECONDS));
            assertInstanceOf(AiAgentExecutionTimeoutException.class, failure.getCause());
        } finally {
            release.countDown();
            blockingService.shutdown();
        }
    }

    private static ErpAiAgentService createService(Duration timeout) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(client);
        when(client.prompt().system(anyString()).messages(anyList())
                .tools(any(Object[].class)).call().content()).thenReturn("answer");
        return new ErpAiAgentService(builder, mock(ErpAiTools.class), timeout, 2, 8);
    }

    private static ErpAiAgentService createBlockingService(
            CountDownLatch entered, CountDownLatch release, Duration timeout) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(client);
        when(client.prompt().system(anyString()).messages(anyList())
                .tools(any(Object[].class)).call().content()).thenAnswer(invocation -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new CancellationException("mock provider call interrupted");
                    }
                    return "answer";
                });
        return new ErpAiAgentService(builder, mock(ErpAiTools.class), timeout, 2, 8);
    }
}
