package com.autoparts.erp.ai.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiConversationResponse(
        UUID id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}