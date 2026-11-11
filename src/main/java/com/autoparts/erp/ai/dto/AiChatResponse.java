
package com.autoparts.erp.ai.dto;

import java.time.Instant;
import java.util.UUID;

public record AiChatResponse(
        UUID conversationId,
        String response,
        Instant timestamp
) {}
