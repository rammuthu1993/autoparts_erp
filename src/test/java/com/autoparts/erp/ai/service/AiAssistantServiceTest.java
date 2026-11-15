package com.autoparts.erp.ai.service;
import com.autoparts.erp.ai.enums.AiAgentRunStatus;
import com.autoparts.erp.ai.repository.AiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private ErpAiAgentService erpAiAgentService;

    @Mock
    private AiRepository aiRepository;

    @InjectMocks
    private AiAssistantService aiAssistantService;

    // ---------------------------------------------
    // TEST 1: AI AGENT FAILURE
    // ---------------------------------------------

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

        // Verify assistant message saved.
        verify(aiRepository).saveMessage(
                conversationId,
                "ASSISTANT",
                assistantResponse
        );

        // Verify run marked COMPLETED.
        verify(aiRepository).updateAgentRunStatus(
                runId,
                AiAgentRunStatus.COMPLETED,
                null
        );

        // FAILED status should not be used.
        verify(aiRepository, never()).updateAgentRunStatus(
                eq(runId),
                eq(AiAgentRunStatus.FAILED),
                anyString()
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
}
