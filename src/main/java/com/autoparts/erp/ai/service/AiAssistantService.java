
package com.autoparts.erp.ai.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

import com.autoparts.erp.ai.dto.AiChatResponse;
import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.dto.AiConversationMessagesResponse;
import com.autoparts.erp.ai.dto.AiMessageResponse;

import com.autoparts.erp.ai.enums.AiAgentRunStatus;
import com.autoparts.erp.ai.exception.AiExecutionInProgressException;
import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.exception.AiProviderTimeoutException;
import com.autoparts.erp.ai.model.AiAgentRun;
import com.autoparts.erp.ai.repository.AiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.autoparts.erp.ai.exception.AiAgentExecutionTimeoutException;

@Service
public class AiAssistantService {

    private final ErpAiAgentService erpAiAgentService;
    private final AiRepository aiRepository;
    private final TransactionTemplate transactionTemplate;
    private static final Logger log =
            LoggerFactory.getLogger(AiAssistantService.class);

    public AiAssistantService(
            ErpAiAgentService erpAiAgentService,
            AiRepository aiRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.erpAiAgentService = erpAiAgentService;
        this.aiRepository = aiRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------
    // CHAT
    // ---------------------------------------------

    public AiChatResponse chat(
            UUID conversationId,
            String message,
            String userSubject
    ) {

        validateMessage(message);
        validateUserSubject(userSubject);

        UUID activeConversationId = conversationId;

        // Create a new conversation if conversationId is null.
        if (activeConversationId == null) {

            activeConversationId = UUID.randomUUID();

            String title = generateConversationTitle(message);

            aiRepository.createConversation(
                    activeConversationId,
                    userSubject,
                    title
            );

        } else {

            // Verify conversation ownership.
            boolean exists = aiRepository.conversationExists(
                    activeConversationId,
                    userSubject
            );

            if (!exists) {
                throw new AccessDeniedException(
                        "Conversation not found or access denied"
                );
            }
        }

        // Save user message.
        aiRepository.saveMessage(
                activeConversationId,
                "USER",
                message
        );

        // Load conversation history.
        List<Map<String, Object>> history =
                aiRepository.getMessages(activeConversationId);

//        List<Message> messages = new ArrayList<>();

//        for (Map<String, Object> item : history) {
//
//            String role = String.valueOf(item.get("role"));
//            String content = String.valueOf(item.get("content"));
//
//            if ("USER".equalsIgnoreCase(role)) {
//
//                messages.add(new UserMessage(content));
//
//            } else if ("ASSISTANT".equalsIgnoreCase(role)) {
//
//                messages.add(new AssistantMessage(content));
//            }
//        }
        
        List<Message> messages = history.stream()
                .map(this::mapToSpringAiMessage)
                .toList();

        // Create agent execution record.
        UUID runId = aiRepository.createAgentRun(
                activeConversationId,
                userSubject
        );

        try {

            // Execute LangGraph4j agent.
            // Retain your existing two-argument method signature.
        	String assistantResponse = erpAiAgentService.chat(
        	        activeConversationId,
        	        runId,
        	        messages
        	);
        	
            if (!StringUtils.hasText(assistantResponse)) {
                throw new IllegalStateException(
                        "AI agent returned an empty response"
                );
            }

            completeRunAndSaveAssistantMessage(
                    activeConversationId,
                    runId,
                    assistantResponse
            );

            return new AiChatResponse(
                    activeConversationId,
                    assistantResponse,
                    Instant.now()
            );

        } catch (AiAgentRunCompletionException exception) {
            log.error(
                    "Unable to persist AI agent completion. Conversation: {}, Run: {}",
                    activeConversationId,
                    runId,
                    exception
            );
            throw exception;

        } catch (AiExecutionInProgressException exception) {
            log.info(
                    "AI execution rejected because the conversation is already active. Conversation: {}, Run: {}",
                    activeConversationId,
                    runId
            );
            recordStatusPreservingException(
                    runId,
                    AiAgentRunStatus.FAILED,
                    exception.getMessage(),
                    exception
            );
            throw exception;

        } catch (AiAgentExecutionTimeoutException exception) {

            log.error(
                    "AI agent execution timed out. Conversation: {}, Run: {}",
                    activeConversationId,
                    runId,
                    exception
            );

            recordStatusPreservingException(
                    runId,
                    AiAgentRunStatus.FAILED,
                    exception.getMessage(),
                    exception
            );

            throw new AiProviderTimeoutException(
                    "AI agent execution exceeded the configured time limit. Please try again.",
                    exception
            );

        } catch (CancellationException exception) {

            log.info(
                    "AI agent execution cancelled. Conversation: {}, Run: {}",
                    activeConversationId,
                    runId
            );

            recordStatusPreservingException(
                    runId,
                    AiAgentRunStatus.CANCELLED,
                    "Execution cancelled by user",
                    exception
            );

            throw exception;

        } catch (RuntimeException exception) {

            log.error(
                    "AI agent execution failed. Conversation: {}, Run: {}",
                    activeConversationId,
                    runId,
                    exception
            );

            String errorMessage = exception.getMessage();

            if (!StringUtils.hasText(errorMessage)) {
                errorMessage = "AI agent execution failed";
            }

            if (errorMessage.length() > 1000) {
                errorMessage = errorMessage.substring(0, 1000);
            }

            recordStatusPreservingException(
                    runId,
                    AiAgentRunStatus.FAILED,
                    errorMessage,
                    exception
            );

            if (isTimeoutException(exception)) {
                throw new AiProviderTimeoutException(
                        "Sarvam AI provider request timed out. Please try again.",
                        exception
                );
            }

            AiProviderResponseException providerResponseException =
                    findCause(exception, AiProviderResponseException.class);
            if (providerResponseException != null) {
                throw new AiProviderResponseException(
                        providerResponseException.getMessage(),
                        exception
                );
            }

            throw exception;
        }
    }

    private static <T extends Throwable> T findCause(
            Throwable exception,
            Class<T> exceptionType
    ) {
        Throwable current = exception;

        while (current != null) {
            if (exceptionType.isInstance(current)) {
                return exceptionType.cast(current);
            }
            current = current.getCause();
        }

        return null;
    }

    private void completeRunAndSaveAssistantMessage(
            UUID conversationId,
            UUID runId,
            String assistantResponse) {
        transactionTemplate.execute(status -> {
            int updated = aiRepository.updateAgentRunStatus(
                    runId,
                    AiAgentRunStatus.COMPLETED,
                    null
            );

            if (updated != 1) {
                AiAgentRun persistedRun = aiRepository.getAgentRun(runId);
                if (updated == 0
                        && persistedRun != null
                        && persistedRun.status() == AiAgentRunStatus.COMPLETED) {
                    return null;
                }

                String persistedStatus = persistedRun == null
                        ? "missing"
                        : persistedRun.status().name();
                throw new AiAgentRunCompletionException(
                        "Unable to mark AI agent run "
                                + runId
                                + " as COMPLETED; persisted status is "
                                + persistedStatus
                );
            }

            aiRepository.saveMessage(
                    conversationId,
                    "ASSISTANT",
                    assistantResponse
            );
            return null;
        });
    }

    private void recordStatusPreservingException(
            UUID runId,
            AiAgentRunStatus status,
            String errorMessage,
            RuntimeException originalException) {
        try {
            aiRepository.updateAgentRunStatus(runId, status, errorMessage);
        } catch (RuntimeException | Error persistenceFailure) {
            if (persistenceFailure != originalException) {
                originalException.addSuppressed(persistenceFailure);
            }
            log.error(
                    "Unable to persist AI agent run status {} for run {}",
                    status,
                    runId,
                    persistenceFailure
            );
        }
    }

    private static class AiAgentRunCompletionException extends IllegalStateException {

        private AiAgentRunCompletionException(String message) {
            super(message);
        }
    }
    
    private Message mapToSpringAiMessage(Map<String, Object> row) {

        String role = String.valueOf(row.get("role"));
        String content = String.valueOf(row.get("content"));

        return switch (role.toUpperCase()) {
            case "USER" -> new UserMessage(content);
            case "ASSISTANT" -> new AssistantMessage(content);
            default -> throw new IllegalArgumentException(
                    "Unsupported message role: " + role
            );
        };
    }
    // ---------------------------------------------
    // GET ALL CONVERSATIONS
    // ---------------------------------------------

    public List<AiConversationResponse> getConversations(
            String userSubject
    ) {

        validateUserSubject(userSubject);

        return aiRepository.getConversations(userSubject);
    }

    // ---------------------------------------------
    // GET CONVERSATION MESSAGES
    // ---------------------------------------------

    public AiConversationMessagesResponse getConversationMessages(
            UUID conversationId,
            String userSubject
    ) {

        validateUserSubject(userSubject);
        validateConversationOwnership(conversationId, userSubject);

        List<AiMessageResponse> messages =
                aiRepository.getConversationMessages(conversationId);

        return new AiConversationMessagesResponse(
                conversationId,
                messages
        );
    }

    // ---------------------------------------------
    // RENAME CONVERSATION
    // ---------------------------------------------

    @Transactional
    public AiConversationResponse renameConversation(
            UUID conversationId,
            String userSubject,
            String title
    ) {

        validateUserSubject(userSubject);

        if (conversationId == null) {
            throw new IllegalArgumentException(
                    "Conversation ID is required"
            );
        }

        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException(
                    "Conversation title cannot be empty"
            );
        }

        String cleanTitle = title.trim();

        if (cleanTitle.length() > 200) {
            throw new IllegalArgumentException(
                    "Conversation title cannot exceed 200 characters"
            );
        }

        int updatedRows = aiRepository.updateConversationTitle(
                conversationId,
                userSubject,
                cleanTitle
        );

        if (updatedRows == 0) {
            throw new AccessDeniedException(
                    "Conversation not found or access denied"
            );
        }

        AiConversationResponse updatedConversation =
                aiRepository.getConversationById(
                        conversationId,
                        userSubject
                );

        if (updatedConversation == null) {
            throw new AccessDeniedException(
                    "Conversation not found or access denied"
            );
        }

        return updatedConversation;
    }

    // ---------------------------------------------
    // ARCHIVE CONVERSATION
    // ---------------------------------------------

    @Transactional
    public void archiveConversation(
            UUID conversationId,
            String userSubject
    ) {

        validateUserSubject(userSubject);

        if (conversationId == null) {
            throw new IllegalArgumentException(
                    "Conversation ID is required"
            );
        }

        int updatedRows = aiRepository.archiveConversation(
                conversationId,
                userSubject
        );

        if (updatedRows == 0) {
            throw new AccessDeniedException(
                    "Conversation not found or access denied"
            );
        }
    }

    // ---------------------------------------------
    // COMMON VALIDATION
    // ---------------------------------------------

    private void validateMessage(String message) {

        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException(
                    "Chat message cannot be empty"
            );
        }
    }

    private void validateUserSubject(String userSubject) {

        if (!StringUtils.hasText(userSubject)) {
            throw new AccessDeniedException(
                    "Authenticated user is required"
            );
        }
    }

    private void validateConversationOwnership(
            UUID conversationId,
            String userSubject
    ) {

        if (conversationId == null
                || !aiRepository.conversationExists(
                        conversationId,
                        userSubject
                )) {

            throw new AccessDeniedException(
                    "Conversation not found or access denied"
            );
        }
    }

    private String generateConversationTitle(String message) {

        String title = message.trim();

        if (title.length() > 100) {
            title = title.substring(0, 100);
        }

        return title;
    }
    
    private boolean isTimeoutException(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) {
                return true;
            }

            String className = current.getClass().getSimpleName();
            String message = current.getMessage();

            if ("InterruptedIOException".equals(className)
                    && message != null
                    && message.toLowerCase().contains("timeout")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
    
    public boolean cancel(
            UUID conversationId,
            String userSubject
    ) {

        validateUserSubject(userSubject);

        boolean exists = aiRepository.conversationExists(
                conversationId,
                userSubject
        );

        if (!exists) {
            throw new AccessDeniedException(
                    "Conversation not found or access denied"
            );
        }

        Optional<UUID> runIdOptional =
                erpAiAgentService.cancel(conversationId);

        if (runIdOptional.isEmpty()) {
            return false;
        }

        UUID runId = runIdOptional.get();

        int updated = aiRepository.updateAgentRunStatus(
                runId,
                AiAgentRunStatus.CANCELLED,
                "Execution cancelled by user"
        );

        if (updated == 1) {
            return true;
        }

        // Another thread may already have changed the status.
        AiAgentRun run = aiRepository.getAgentRun(runId);

        return run != null
                && run.status() == AiAgentRunStatus.CANCELLED;
    }
}
