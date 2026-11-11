package com.autoparts.erp.ai.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.dto.AiChatResponse;
import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.dto.AiConversationMessagesResponse;
import com.autoparts.erp.ai.dto.AiMessageResponse;
import com.autoparts.erp.ai.repository.AiRepository;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;

@Service
public class AiAssistantService {

    private final ErpAiAgentService erpAiAgentService;
    private final AiRepository aiRepository;

    public AiAssistantService(
            ErpAiAgentService erpAiAgentService,
            AiRepository aiRepository
    ) {
        this.erpAiAgentService = erpAiAgentService;
        this.aiRepository = aiRepository;
    }

    // ---------------------------------------------
    // CHAT
    // ---------------------------------------------

    public AiChatResponse chat(
            UUID conversationId,
            String message,
            String userSubject
    ) {

        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException(
                    "Chat message cannot be empty"
            );
        }

        if (!StringUtils.hasText(userSubject)) {
            throw new AccessDeniedException(
                    "Authenticated user is required"
            );
        }

        boolean newConversation = conversationId == null;

        if (newConversation) {

            conversationId = UUID.randomUUID();

            String title = message.trim();

            if (title.length() > 100) {
                title = title.substring(0, 100);
            }

            aiRepository.createConversation(
                    conversationId,
                    userSubject,
                    title
            );

        } else if (!aiRepository.conversationExists(
                conversationId,
                userSubject
        )) {

            throw new AccessDeniedException(
                    "Conversation not found or access denied"
            );
        }

        // Save current user message

        aiRepository.saveMessage(
                conversationId,
                "USER",
                message
        );

        // Load latest 20 messages for AI context

        List<Map<String, Object>> history =
                aiRepository.getMessages(conversationId);

        List<Message> messages = new ArrayList<>();

        for (Map<String, Object> item : history) {

            String role = String.valueOf(item.get("role"));
            String content = String.valueOf(item.get("content"));

            if ("USER".equalsIgnoreCase(role)) {

                messages.add(new UserMessage(content));

            } else if ("ASSISTANT".equalsIgnoreCase(role)) {

                messages.add(new AssistantMessage(content));
            }
        }

        // Execute AI agent

        String response = erpAiAgentService.chat(messages);

        if (!StringUtils.hasText(response)) {
            throw new IllegalStateException(
                    "AI agent returned an empty response"
            );
        }

        // Save assistant response

        aiRepository.saveMessage(
                conversationId,
                "ASSISTANT",
                response
        );

        return new AiChatResponse(
                conversationId,
                response,
                Instant.now()
        );
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

        // Update only if the conversation belongs to this user
        // and is still active.

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

        // Return the updated conversation.

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
    // COMMON VALIDATION
    // ---------------------------------------------

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
}