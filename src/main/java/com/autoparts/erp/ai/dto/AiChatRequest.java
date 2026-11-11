
package com.autoparts.erp.ai.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(

    UUID conversationId,

    @NotBlank(message = "Message is required")
    String message

) {}
