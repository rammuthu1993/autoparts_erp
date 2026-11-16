package com.autoparts.erp.ai.service;
import com.autoparts.erp.ai.enums.AiAgentRunStatus;
import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;
import com.autoparts.erp.ai.exception.AiExecutionInProgressException;
import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.exception.AiProviderTimeoutException;
import com.autoparts.erp.ai.model.AiAgentRun;
import com.autoparts.erp.ai.repository.AiRepository;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private ErpAiAgentService erpAiAgentService;

    @Mock
    private AiRepository aiRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private AiAssistantService aiAssistantService;

    // ---------------------------------------------
    // TEST 1: AI AGENT FAILURE
    // ---------------------------------------------

    @Test
    void shouldMarkRejectedConcurrentRunAsFailed() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String message = "Find product GAP-000001";
        var exception = new AiExecutionInProgressException(
                "An AI execution is already active for this conversation"
        );

        when(aiRepository.conversationExists(conversationId, "admin")).thenReturn(true);
        when(aiRepository.getMessages(conversationId)).thenReturn(List.of(
                Map.of("role", "USER", "content", message)
        ));
        when(aiRepository.createAgentRun(conversationId, "admin")).thenReturn(runId);
        when(erpAiAgentService.chat(eq(conversationId), any(UUID.class), anyList()))
                .thenThrow(exception);
        when(aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.FAILED,
                exception.getMessage()
        )).thenReturn(1);

        AiExecutionInProgressException thrown = assertThrows(
                AiExecutionInProgressException.class,
                () -> aiAssistantService.chat(conversationId, message, "admin")
        );

        assertEquals(exception, thrown);
        verify(aiRepository).updateAgentRunStatus(
                runId,
                AiAgentRunStatus.FAILED,
                exception.getMessage()
        );
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );
    }

    @Test
    void shouldMarkAgentRunAsFailedWhenAgentFails() {

        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        String message = "Find product GAP-000001";
        String userSubject = "admin";

        when(aiRepository.conversationExists(
                conversationId,
                userSubject
        )).thenReturn(true);

        when(aiRepository.getMessages(conversationId))
                .thenReturn(List.of(
                        Map.of(
                                "role", "USER",
                                "content", message
                        )
                ));

        when(aiRepository.createAgentRun(
                conversationId,
                userSubject
        )).thenReturn(runId);

        // Simulate AI provider failure.
        when(erpAiAgentService.chat(
                eq(conversationId),
                any(UUID.class),
                anyList()
        )).thenThrow(
                new IllegalStateException(
                        "AI provider unavailable"
                )
        );
        
        when(aiRepository.updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        )).thenReturn(1);

        // Verify exception.
        assertThrows(
                IllegalStateException.class,
                () -> aiAssistantService.chat(
                        conversationId,
                        message,
                        userSubject
                )
        );

        // User message should be saved.
        verify(aiRepository).saveMessage(
                conversationId,
                "USER",
                message
        );

        // Assistant message should NOT be saved.
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );

        // Agent run should be marked FAILED.
        verify(aiRepository).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );

        // Agent run should have been created.
        verify(aiRepository).createAgentRun(
                conversationId,
                userSubject
        );
    }

    @Test
    void preservesProviderFailureWhenFailedStatusPersistenceAlsoFails() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        IllegalStateException providerFailure =
                new IllegalStateException("AI provider unavailable");
        IllegalStateException statusFailure =
                new IllegalStateException("run status update failed");

        when(aiRepository.conversationExists(conversationId, "admin")).thenReturn(true);
        when(aiRepository.getMessages(conversationId)).thenReturn(List.of(
                Map.of("role", "USER", "content", "Find product GAP-000001")
        ));
        when(aiRepository.createAgentRun(conversationId, "admin")).thenReturn(runId);
        when(erpAiAgentService.chat(eq(conversationId), any(UUID.class), anyList()))
                .thenThrow(providerFailure);
        doThrow(statusFailure).when(aiRepository).updateAgentRunStatus(
                runId,
                AiAgentRunStatus.FAILED,
                "AI provider unavailable"
        );

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> aiAssistantService.chat(
                        conversationId,
                        "Find product GAP-000001",
                        "admin"
                )
        );

        assertEquals(providerFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals(statusFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void mapsWrappedEmptyProviderResponseAfterMarkingRunFailed() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AiProviderResponseException providerFailure =
                new AiProviderResponseException(
                        "AI provider returned an empty response. Please try again."
                );

        when(aiRepository.conversationExists(conversationId, "admin")).thenReturn(true);
        when(aiRepository.getMessages(conversationId)).thenReturn(List.of(
                Map.of("role", "USER", "content", "Hello")
        ));
        when(aiRepository.createAgentRun(conversationId, "admin")).thenReturn(runId);
        when(erpAiAgentService.chat(eq(conversationId), any(UUID.class), anyList()))
                .thenThrow(new CompletionException(
                        new IllegalStateException("Graph execution failed", providerFailure)
                ));
        when(aiRepository.updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        )).thenReturn(1);

        AiProviderResponseException thrown = assertThrows(
                AiProviderResponseException.class,
                () -> aiAssistantService.chat(conversationId, "Hello", "admin")
        );

        assertEquals(providerFailure.getMessage(), thrown.getMessage());
        assertEquals(
                providerFailure,
                thrown.getCause().getCause().getCause()
        );
        verify(aiRepository).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );
    }

    // ---------------------------------------------
    // TEST 2: SUCCESSFUL AI AGENT EXECUTION
    // ---------------------------------------------

    @Test
    void shouldMarkAgentRunAsCompletedWhenAgentSucceeds() {

        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        String message = "Find product GAP-000001";
        String userSubject = "admin";
        String assistantResponse = "Product found successfully.";

        when(aiRepository.conversationExists(
                conversationId,
                userSubject
        )).thenReturn(true);

        when(aiRepository.getMessages(conversationId))
                .thenReturn(List.of(
                        Map.of(
                                "role", "USER",
                                "content", message
                        )
                ));

        when(aiRepository.createAgentRun(
                conversationId,
                userSubject
        )).thenReturn(runId);

        when(erpAiAgentService.chat(
                eq(conversationId),
                any(UUID.class),
                anyList()
        )).thenReturn(assistantResponse);

        when(aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.COMPLETED,
                null
        )).thenReturn(1);

        // Execute chat.
        aiAssistantService.chat(
                conversationId,
                message,
                userSubject
        );

        // Verify user message saved.
        verify(aiRepository).saveMessage(
                conversationId,
                "USER",
                message
        );

        InOrder completionOrder = inOrder(aiRepository);
        completionOrder.verify(aiRepository).updateAgentRunStatus(
                runId,
                AiAgentRunStatus.COMPLETED,
                null
        );
        completionOrder.verify(aiRepository).saveMessage(
                conversationId,
                "ASSISTANT",
                assistantResponse
        );
        verify(transactionManager).commit(null);

        // FAILED status should not be used.
        verify(aiRepository, never()).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
    }

    @Test
    void acceptsZeroCompletedUpdateWhenRunIsAlreadyPersistedAsCompleted() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String message = "Find product GAP-000001";
        String response = "Product found successfully.";

        when(aiRepository.conversationExists(conversationId, "admin")).thenReturn(true);
        when(aiRepository.getMessages(conversationId)).thenReturn(List.of(
                Map.of("role", "USER", "content", message)
        ));
        when(aiRepository.createAgentRun(conversationId, "admin")).thenReturn(runId);
        when(erpAiAgentService.chat(eq(conversationId), any(UUID.class), anyList()))
                .thenReturn(response);
        when(aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.COMPLETED,
                null
        )).thenReturn(0);
        when(aiRepository.getAgentRun(runId)).thenReturn(new AiAgentRun(
                runId,
                conversationId,
                "admin",
                AiAgentRunStatus.COMPLETED,
                1,
                null,
                null,
                null
        ));

        aiAssistantService.chat(conversationId, message, "admin");

        verify(aiRepository).getAgentRun(runId);
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );
    }

    @Test
    void failsWhenCompletedUpdateDidNotPersistAndRunIsNotCompleted() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String message = "Find product GAP-000001";
        String response = "Product found successfully.";

        when(aiRepository.conversationExists(conversationId, "admin")).thenReturn(true);
        when(aiRepository.getMessages(conversationId)).thenReturn(List.of(
                Map.of("role", "USER", "content", message)
        ));
        when(aiRepository.createAgentRun(conversationId, "admin")).thenReturn(runId);
        when(erpAiAgentService.chat(eq(conversationId), any(UUID.class), anyList()))
                .thenReturn(response);
        when(aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.COMPLETED,
                null
        )).thenReturn(0);
        when(aiRepository.getAgentRun(runId)).thenReturn(new AiAgentRun(
                runId,
                conversationId,
                "admin",
                AiAgentRunStatus.CANCELLED,
                1,
                "Execution cancelled by user",
                null,
                null
        ));
        assertThrows(
                IllegalStateException.class,
                () -> aiAssistantService.chat(conversationId, message, "admin")
        );

        verify(aiRepository).getAgentRun(runId);
        verify(aiRepository, never()).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );
    }

    @Test
    void assistantMessagePersistenceFailureRollsBackCompletionTransaction() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String message = "Find product GAP-000001";
        RuntimeException persistenceFailure = new IllegalStateException("message insert failed");

        when(aiRepository.conversationExists(conversationId, "admin")).thenReturn(true);
        when(aiRepository.getMessages(conversationId)).thenReturn(List.of(
                Map.of("role", "USER", "content", message)
        ));
        when(aiRepository.createAgentRun(conversationId, "admin")).thenReturn(runId);
        when(erpAiAgentService.chat(eq(conversationId), any(UUID.class), anyList()))
                .thenReturn("Product found successfully.");
        when(aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.COMPLETED,
                null
        )).thenReturn(1);
        lenient().doThrow(persistenceFailure).when(aiRepository).saveMessage(
                conversationId,
                "ASSISTANT",
                "Product found successfully."
        );
        when(aiRepository.updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        )).thenReturn(1);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> aiAssistantService.chat(conversationId, message, "admin")
        );

        assertEquals(persistenceFailure, thrown);
        verify(transactionManager).rollback(null);
        verify(aiRepository).updateAgentRunStatus(
                runId,
                AiAgentRunStatus.FAILED,
                "message insert failed"
        );
    }

    // ---------------------------------------------
    // TEST 3: CONVERSATION OWNERSHIP VALIDATION
    // ---------------------------------------------

    @Test
    void shouldNotCreateAgentRunWhenConversationIsNotOwnedByUser() {

        UUID conversationId = UUID.randomUUID();

        when(aiRepository.conversationExists(
                conversationId,
                "admin"
        )).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> aiAssistantService.chat(
                        conversationId,
                        "Find product GAP-000001",
                        "admin"
                )
        );

        // Agent run should NOT be created.
        verify(aiRepository, never()).createAgentRun(
                eq(conversationId),
                anyString()
        );

        // AI agent should NOT execute.
        verify(aiRepository, never()).getMessages(
                conversationId
        );
    }

    // ---------------------------------------------
    // TEST 4: EMPTY AI RESPONSE
    // ---------------------------------------------

    @Test
    void shouldMarkAgentRunAsFailedWhenAgentReturnsEmptyResponse() {

        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        String message = "Find product GAP-000001";

        when(aiRepository.conversationExists(
                conversationId,
                "admin"
        )).thenReturn(true);

        when(aiRepository.getMessages(conversationId))
                .thenReturn(List.of(
                        Map.of(
                                "role", "USER",
                                "content", message
                        )
                ));

        when(aiRepository.createAgentRun(
                conversationId,
                "admin"
        )).thenReturn(runId);

        when(erpAiAgentService.chat(
                eq(conversationId),
                any(UUID.class),
                anyList()
        )).thenReturn(" ");

        when(aiRepository.updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> aiAssistantService.chat(
                        conversationId,
                        message,
                        "admin"
                )
        );

        // Assistant response should not be saved.
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );

        // Run should be marked FAILED.
        verify(aiRepository).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
    }

    @Test
    void shouldRecordShutdownInterruptionAsFailedNotUserCancelled() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String message = "Find product GAP-000001";

        when(aiRepository.conversationExists(conversationId, "admin"))
                .thenReturn(true);
        when(aiRepository.getMessages(conversationId))
                .thenReturn(List.of(Map.of(
                        "role", "USER",
                        "content", message
                )));
        when(aiRepository.createAgentRun(conversationId, "admin"))
                .thenReturn(runId);
        when(erpAiAgentService.chat(
                eq(conversationId),
                eq(runId),
                anyList()
        )).thenThrow(new IllegalStateException(
                "AI agent execution was interrupted by application shutdown"
        ));
        when(aiRepository.updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> aiAssistantService.chat(conversationId, message, "admin")
        );

        verify(aiRepository).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
        verify(aiRepository, never()).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.CANCELLED),
                anyString()
        );
        verify(aiRepository, never()).saveMessage(
                eq(conversationId),
                eq("ASSISTANT"),
                anyString()
        );
    }

    @Test
    void shouldKeepTimeoutRunStatusFailed() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String message = "Find product GAP-000001";

        when(aiRepository.conversationExists(conversationId, "admin"))
                .thenReturn(true);
        when(aiRepository.getMessages(conversationId))
                .thenReturn(List.of(Map.of(
                        "role", "USER",
                        "content", message
                )));
        when(aiRepository.createAgentRun(conversationId, "admin"))
                .thenReturn(runId);
        when(erpAiAgentService.chat(
                eq(conversationId),
                eq(runId),
                anyList()
        )).thenThrow(new AiAgentExecutionTimeoutException("timed out"));
        when(aiRepository.updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        )).thenReturn(1);

        assertThrows(
                AiProviderTimeoutException.class,
                () -> aiAssistantService.chat(conversationId, message, "admin")
        );

        verify(aiRepository).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
        verify(aiRepository, never()).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.CANCELLED),
                anyString()
        );
    }

    @Test
    void shouldRejectInvalidExecutionTimeoutConfiguration() {
        assertThrows(
                IllegalStateException.class,
                () -> new ErpAiAgentService(
                        null,
                        mock(com.autoparts.erp.ai.tools.ErpAiTools.class),
                        Duration.ZERO,
                        1,
                        1)
        );
    }

    @Test
    void shouldReturnFalseWhenCancellationIsRejected() {
        UUID conversationId = UUID.randomUUID();

        when(aiRepository.conversationExists(
                conversationId,
                "admin"
        )).thenReturn(true);

        when(erpAiAgentService.cancel(conversationId))
                .thenReturn(java.util.Optional.empty());

        boolean cancelled = aiAssistantService.cancel(
                conversationId,
                "admin"
        );

        assertFalse(cancelled);

        verify(aiRepository, never()).updateAgentRunStatus(
                any(UUID.class),
                eq(AiAgentRunStatus.CANCELLED),
                anyString()
        );
    }

    @Test
    void repeatedCancellationAcceptsPersistedCancelledStatus() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        when(aiRepository.conversationExists(conversationId, "admin"))
                .thenReturn(true);
        when(erpAiAgentService.cancel(conversationId))
                .thenReturn(java.util.Optional.of(runId));
        when(aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.CANCELLED,
                "Execution cancelled by user"
        )).thenReturn(0);
        when(aiRepository.getAgentRun(runId)).thenReturn(new AiAgentRun(
                runId,
                conversationId,
                "admin",
                AiAgentRunStatus.CANCELLED,
                1,
                "Execution cancelled by user",
                null,
                null
        ));

        assertTrue(aiAssistantService.cancel(conversationId, "admin"));
        verify(aiRepository, never()).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
        );
    }
}
