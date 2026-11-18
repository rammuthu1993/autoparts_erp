package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.autoparts.erp.ai.enums.ClassificationSource;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErpAiLlmClassifierTest {

    private ChatModel chatModel;
    private ObjectMapper objectMapper;
    private ErpAiLlmClassifier classifier;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        objectMapper = new ObjectMapper();
        classifier = new ErpAiLlmClassifier(chatModel, objectMapper);
    }

    private void mockChatResponse(String output) {
        Generation generation = new Generation(new AssistantMessage(output));
        ChatResponse response = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    @Test
    @DisplayName("Should successfully parse valid structured JSON from LLM")
    void shouldParseValidJson() {
        String json = """
                {
                  "intent": "PURCHASES_BY_SUPPLIER",
                  "confidence": 0.92,
                  "searchText": "Bosch Auto Parts",
                  "startDate": "",
                  "endDate": "",
                  "limit": 10,
                  "productIdentifiers": [],
                  "clarificationQuestion": "",
                  "isMultiIntent": false,
                  "secondaryIntent": ""
                }
                """;
        mockChatResponse(json);

        Optional<ErpAiIntentResult> result = classifier.classify("What did we purchase from Bosch?", List.of());

        assertTrue(result.isPresent());
        assertEquals("PURCHASES_BY_SUPPLIER", result.get().intent());
        assertEquals("Bosch Auto Parts", result.get().searchText());
        assertEquals(0.92, result.get().confidence(), 0.001);
        assertEquals(ClassificationSource.LLM_FALLBACK, result.get().source());
        assertFalse(result.get().isMultiIntent());
    }

    @Test
    @DisplayName("Should reject hallucinated intent not present in SUPPORTED_INTENTS")
    void shouldRejectHallucinatedIntent() {
        String json = """
                {
                  "intent": "UNKNOWN_CUSTOM_REPORT",
                  "confidence": 0.99,
                  "searchText": "sales"
                }
                """;
        mockChatResponse(json);

        Optional<ErpAiIntentResult> result = classifier.classify("Show custom report", List.of());

        assertTrue(result.isEmpty(), "Unsupported intent must be rejected");
    }

    @Test
    @DisplayName("Should reject non-finite confidence values such as NaN or Infinity")
    void shouldRejectNonFiniteConfidence() {
        // String containing NaN for confidence
        String nanJson = """
                {
                  "intent": "PRODUCT_STOCK",
                  "confidence": "NaN",
                  "searchText": "GAP-000001"
                }
                """;
        mockChatResponse(nanJson);

        Optional<ErpAiIntentResult> nanResult = classifier.classify("stock of GAP-000001", List.of());
        assertTrue(nanResult.isEmpty(), "NaN confidence must be rejected");

        // String containing Infinity for confidence
        String infJson = """
                {
                  "intent": "PRODUCT_STOCK",
                  "confidence": "Infinity",
                  "searchText": "GAP-000001"
                }
                """;
        mockChatResponse(infJson);

        Optional<ErpAiIntentResult> infResult = classifier.classify("stock of GAP-000001", List.of());
        assertTrue(infResult.isEmpty(), "Infinity confidence must be rejected");
    }

    @Test
    @DisplayName("Should gracefully handle malformed or non-JSON output")
    void shouldHandleMalformedJson() {
        mockChatResponse("Sorry, I don't know how to classify this query.");

        Optional<ErpAiIntentResult> result = classifier.classify("Ambiguous query", List.of());

        assertTrue(result.isEmpty(), "Non-JSON response must return empty Optional");
    }

    @Test
    @DisplayName("Should gracefully handle provider exception or timeout")
    void shouldHandleProviderException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Connection timed out"));

        Optional<ErpAiIntentResult> result = classifier.classify("Query during network outage", List.of());

        assertTrue(result.isEmpty(), "Provider exception must fail-safe to empty Optional");
    }

    @Test
    @DisplayName("Should validate and clamp limits and parse ISO dates")
    void shouldClampLimitAndValidateDates() {
        String json = """
                {
                  "intent": "EXPENSE_SUMMARY",
                  "confidence": 0.85,
                  "startDate": "2026-11-01",
                  "endDate": "not-a-valid-date",
                  "limit": 100
                }
                """;
        mockChatResponse(json);

        Optional<ErpAiIntentResult> result = classifier.classify("Expense summary", List.of());

        assertTrue(result.isPresent());
        assertEquals("EXPENSE_SUMMARY", result.get().intent());
        assertEquals("2026-11-01", result.get().startDate());
        assertEquals("", result.get().endDate(), "Invalid date must be cleared");
        assertEquals(50, result.get().limit(), "Limit > 50 must be clamped to 50");
    }

    @Test
    @DisplayName("Should parse multi-intent metadata correctly")
    void shouldParseMultiIntent() {
        String json = """
                {
                  "intent": "PRODUCT_STOCK",
                  "confidence": 0.88,
                  "searchText": "OIL-001",
                  "isMultiIntent": true,
                  "secondaryIntent": "SALES_SEARCH"
                }
                """;
        mockChatResponse(json);

        Optional<ErpAiIntentResult> result = classifier.classify("Show stock of OIL-001 and recent sales", List.of());

        assertTrue(result.isPresent());
        assertEquals("PRODUCT_STOCK", result.get().intent());
        assertTrue(result.get().isMultiIntent());
        assertEquals("SALES_SEARCH", result.get().secondaryIntent());
    }
}
