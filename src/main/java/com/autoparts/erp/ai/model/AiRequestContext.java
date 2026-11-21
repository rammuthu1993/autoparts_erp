package com.autoparts.erp.ai.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.chat.messages.Message;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

/**
 * Encapsulates the execution context of an active AI request.
 */
public record AiRequestContext(
        UUID conversationId,
        UUID runId,
        String userSubject,
        Set<String> authorities,
        List<Message> messages,
        String currentMessage,
        ErpAiExecutionContext executionContext
) {
    public static AiRequestContext create(
            UUID conversationId,
            UUID runId,
            List<Message> messages,
            ErpAiExecutionContext executionContext
    ) {
        String userSubject = executionContext != null ? executionContext.userSubject() : "";
        Set<String> authorities = executionContext != null ? executionContext.authorities() : Set.of();
        String current = findLatestUserMessage(messages);
        return new AiRequestContext(
                conversationId,
                runId,
                userSubject,
                authorities,
                messages != null ? List.copyOf(messages) : List.of(),
                current,
                executionContext
        );
    }

    private static String findLatestUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof org.springframework.ai.chat.messages.UserMessage) {
                return msg.getText() != null ? msg.getText() : "";
            }
        }
        return messages.get(messages.size() - 1).getText() != null
                ? messages.get(messages.size() - 1).getText()
                : "";
    }
}
