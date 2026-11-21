package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Keeps the newest conversation messages within an approximate token budget.
 * The 4-UTF-8-bytes-per-token estimate is deliberately documented as a heuristic;
 * exact tokenization remains provider/model specific.
 */
@Component
public class AiConversationHistoryBudget {

    @Value("${app.ai.history.max-tokens:2000}")
    private int maxTokens = 2000;

    public List<Message> trim(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int remainingTokens = Math.max(1, Math.min(8000, maxTokens));
        List<Message> newestFirst = new ArrayList<>();

        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            String content = message.getText() == null ? "" : message.getText();
            if (content.isEmpty()) {
                newestFirst.add(message);
                continue;
            }
            int messageTokens = estimateTokens(content);
            if (messageTokens > remainingTokens) {
                if (newestFirst.isEmpty() && remainingTokens > 0) {
                    String tail = tailWithinTokenBudget(content, remainingTokens);
                    newestFirst.add(message instanceof UserMessage
                            ? new UserMessage(tail)
                            : new AssistantMessage(tail));
                }
                break;
            }
            newestFirst.add(message);
            remainingTokens -= messageTokens;
            if (remainingTokens <= 0) {
                break;
            }
        }

        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    private static int estimateTokens(String text) {
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        return Math.max(1, (bytes + 3) / 4);
    }

    private static String tailWithinTokenBudget(String text, int tokenBudget) {
        int byteBudget = tokenBudget * 4;
        int start = text.length();
        int usedBytes = 0;
        while (start > 0) {
            int codePoint = text.codePointBefore(start);
            int charCount = Character.charCount(codePoint);
            int codePointBytes = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + codePointBytes > byteBudget) {
                break;
            }
            usedBytes += codePointBytes;
            start -= charCount;
        }
        return text.substring(start);
    }
}
