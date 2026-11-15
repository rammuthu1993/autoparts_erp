
package com.autoparts.erp.ai.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.autoparts.erp.ai.enums.AiAgentRunStatus;

public record AiAgentRun(

        UUID id,

        UUID conversationId,

        String userSubject,

        AiAgentRunStatus status,

        int attemptCount,

        String errorMessage,

        OffsetDateTime startedAt,

        OffsetDateTime completedAt

) {
}
