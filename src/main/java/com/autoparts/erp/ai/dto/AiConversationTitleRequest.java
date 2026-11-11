package com.autoparts.erp.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiConversationTitleRequest(

        @NotBlank(message = "Conversation title is required")
        @Size(
                max = 200,
                message = "Conversation title cannot exceed 200 characters"
        )
        String title

) {
}