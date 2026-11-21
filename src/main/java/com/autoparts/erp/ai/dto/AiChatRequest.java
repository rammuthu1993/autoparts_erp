
package com.autoparts.erp.ai.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(

    UUID conversationId,

    @NotBlank(message = "Message is required")
    @Size(max = 8000, message = "Message cannot exceed 8000 characters")
    String message

) {}
