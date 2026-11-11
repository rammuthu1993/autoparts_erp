package com.autoparts.erp.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiConversationMessagesResponse(
        UUID conversationId,
        List<AiMessageResponse> messages
) {}