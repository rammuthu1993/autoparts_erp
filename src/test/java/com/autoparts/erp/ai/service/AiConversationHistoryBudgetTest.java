package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

class AiConversationHistoryBudgetTest {

    @Test
    void keepsNewestMessagesWithinApproximateBudget() {
        AiConversationHistoryBudget budget = new AiConversationHistoryBudget();
        ReflectionTestUtils.setField(budget, "maxTokens", 2);

        List<Message> messages = List.of(
                new UserMessage("older message"),
                new AssistantMessage("old response"),
                new UserMessage("newest!!")
        );

        List<Message> trimmed = budget.trim(messages);

        assertEquals(1, trimmed.size());
        assertEquals("newest!!", trimmed.get(0).getText());
    }

    @Test
    void truncatesAnOversizedNewestMessageFromTheBeginning() {
        AiConversationHistoryBudget budget = new AiConversationHistoryBudget();
        ReflectionTestUtils.setField(budget, "maxTokens", 2);

        List<Message> trimmed = budget.trim(List.of(
                new UserMessage("0123456789abcdef")
        ));

        assertEquals("89abcdef", trimmed.get(0).getText());
    }
}
