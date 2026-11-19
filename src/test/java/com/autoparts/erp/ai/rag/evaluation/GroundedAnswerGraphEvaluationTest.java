package com.autoparts.erp.ai.rag.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.autoparts.erp.ai.rag.citation.CitationValidator;
import com.autoparts.erp.ai.rag.model.RetrievedChunk;
import com.autoparts.erp.ai.rag.retrieval.KnowledgeRetrievalService;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.service.ErpAiGraphNodes;
import com.autoparts.erp.ai.service.ErpAiGraphState;

/** Executes the real knowledge graph nodes with deterministic retrieval and provider doubles. */
class GroundedAnswerGraphEvaluationTest {
    private ChatModel chatModel;
    private KnowledgeRetrievalService retrievalService;
    private ErpAiGraphNodes nodes;
    private RunnableConfig config;
    private RetrievedChunk chunk;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        retrievalService = mock(KnowledgeRetrievalService.class);
        nodes = new ErpAiGraphNodes(chatModel, null, null, null, null, null, null, null,
                null, null, null, null, null, retrievalService, new CitationValidator());
        config = RunnableConfig.builder()
                .addMetadata("executionContext", ErpAiExecutionContext.create(
                        UUID.randomUUID(), "phase5-evaluator", List.of("ROLE_ADMIN")))
                .build();
        chunk = new RetrievedChunk(9001L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A 15% restocking fee applies to special-order parts.", 20, 0.99);
    }

    @Test
    void evaluatesTheValidatedUserVisibleAnswerRatherThanTheRawModelDraft() {
        when(retrievalService.retrieveKnowledge(eq("What is the special-order restocking fee?"), any(), eq(4)))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "Special-order parts incur a 15% restocking fee [Source: Return and Refund Policy v1, §Restocking Fee]."));

        ErpAiGraphState state = state("What is the special-order restocking fee?");
        state = apply(state, nodes.retrieveKnowledge(state, config));
        state = apply(state, nodes.validateRetrievedContext(state, config));
        state = apply(state, nodes.generateGroundedResponse(state, config));
        String rawModelResponse = state.finalResponse();
        state = apply(state, nodes.validateCitations(state, config));

        assertEquals(rawModelResponse, state.finalResponse());
        assertTrue(state.finalResponse().contains("[Source: Return and Refund Policy v1, §Restocking Fee]"));
        var citationResult = new CitationValidator().validateCitations(state.finalResponse(), state.retrievedChunks());
        assertTrue(citationResult.valid());
        assertEquals(1, citationResult.matchedCitations().size());
    }

    @Test
    void fabricatedCitationAttemptIsNotCountedAsAUserVisibleCitation() {
        when(retrievalService.retrieveKnowledge(eq("Explain the warranty exception."), any(), eq(4)))
                .thenReturn(List.of(chunk));
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "The warranty exception lasts 90 days [Source: Invented Warranty Manual v9, §Exceptions]."));

        ErpAiGraphState state = state("Explain the warranty exception.");
        state = apply(state, nodes.retrieveKnowledge(state, config));
        state = apply(state, nodes.generateGroundedResponse(state, config));
        String rawModelResponse = state.finalResponse();
        assertTrue(rawModelResponse.contains("Invented Warranty Manual"));
        state = apply(state, nodes.validateCitations(state, config));

        assertFalse(state.finalResponse().contains("Invented Warranty Manual"));
        assertTrue(state.finalResponse().toLowerCase().contains("sufficient authorized documentation"));
        assertEquals(0, countSourceCitations(state.finalResponse()));
    }

    @Test
    void noRetrievalProducesAnAbstentionWithoutCallingTheProvider() {
        when(retrievalService.retrieveKnowledge(eq("What is the leave entitlement?"), any(), eq(4)))
                .thenReturn(List.of());

        ErpAiGraphState state = state("What is the leave entitlement?");
        state = apply(state, nodes.retrieveKnowledge(state, config));
        state = apply(state, nodes.validateRetrievedContext(state, config));
        state = apply(state, nodes.generateGroundedResponse(state, config));
        state = apply(state, nodes.validateCitations(state, config));

        assertTrue(state.finalResponse().toLowerCase().contains("could not find"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ErpAiGraphState state(String question) {
        return new ErpAiGraphState(Map.of("currentMessage", question));
    }

    private static ErpAiGraphState apply(ErpAiGraphState state, Map<String, Object> update) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("currentMessage", state.currentMessage());
        merged.put("retrievedChunks", state.retrievedChunks());
        merged.put("finalResponse", state.finalResponse());
        merged.putAll(update);
        return new ErpAiGraphState(merged);
    }

    private static int countSourceCitations(String text) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf("[Source:", from)) >= 0) {
            count++;
            from += 8;
        }
        return count;
    }
}
