package com.autoparts.erp.ai.repository;

import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.dto.AiMessageResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiRepository {

    void createConversation(
            UUID id,
            String userSubject,
            String title
    );

    boolean conversationExists(
            UUID id,
            String userSubject
    );

    int updateConversationTitle(
            UUID id,
            String userSubject,
            String title
    );

    AiConversationResponse getConversationById(
            UUID id,
            String userSubject
    );

    void saveMessage(
            UUID conversationId,
            String role,
            String content
    );

    List<Map<String, Object>> getMessages(
            UUID conversationId
    );

    List<AiConversationResponse> getConversations(
            String userSubject
    );

    List<AiMessageResponse> getConversationMessages(
            UUID conversationId
    );

    void saveToolRun(
            UUID conversationId,
            String userSubject,
            String toolName,
            String arguments,
            String result,
            String status,
            String errorMessage
    );

    int archiveConversation(
            UUID id,
            String userSubject
    );
}