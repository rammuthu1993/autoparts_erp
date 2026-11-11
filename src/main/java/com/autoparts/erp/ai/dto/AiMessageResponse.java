package com.autoparts.erp.ai.dto;

import java.time.OffsetDateTime;

public record AiMessageResponse(
        Long id,
        String role,
        String content,
        OffsetDateTime createdAt
) {}