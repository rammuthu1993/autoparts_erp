package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;
import com.autoparts.erp.ai.tools.ProductLookupTool;

@ExtendWith(MockitoExtension.class)
class ErpAiComparisonAndContextTest {

    @Mock ChatModel chatModel;
    @Mock ProductLookupTool productLookupTool;
    @Mock InventoryHistoryLookupTool inventoryHistoryLookupTool;

    private ErpAiIntentClassifier classifier;
    private ErpAiGraphNodes graphNodes;

    @BeforeEach
    void setUp() {
        classifier = new ErpAiIntentClassifier();
        graphNodes = new ErpAiGraphNodes(
                chatModel, productLookupTool, classifier, inventoryHistoryLookupTool);
    }

    @Test
    void classifiesTwoProductComparisonAndPreservesBothIdentifiers() {
        ErpAiIntentResult result = classifier.classify("Compare GAP-000001 and GAP-000002");
        assertEquals("PRODUCT_COMPARISON", result.intent());
        assertEquals(List.of("GAP-000001", "GAP-000002"), result.productIdentifiers());
    }

    @Test
    void asksForTwoIdentifiersWhenComparisonHasOnlyOne() {
        ErpAiIntentResult result = classifier.classify("Compare GAP-000001");
        assertEquals("CLARIFICATION", result.intent());
        assertTrue(result.clarificationMessage().contains("at least two"));
    }

    @Test
    void asksToLimitComparisonToFiveProducts() {
        ErpAiIntentResult result = classifier.classify(
                "Compare A-001, A-002, A-003, A-004, A-005 and A-006");
        assertEquals("CLARIFICATION", result.intent());
        assertTrue(result.clarificationMessage().contains("five"));
    }

    @Test
    void routesComparisonToTool() {
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(
                new ErpAiGraphState(Map.of("intent", "PRODUCT_COMPARISON"))));
    }

    @Test
    void classificationCarriesBothIdentifiersIntoGraphState() {
        Map<String, Object> result = graphNodes.classifyIntent(
                new ErpAiGraphState(Map.of(
                        "currentMessage", "Compare GAP-000001 and GAP-000002",
                        "history", List.of())),
                RunnableConfig.builder().build());
        assertEquals("PRODUCT_COMPARISON", result.get("intent"));
        assertEquals(List.of("GAP-000001", "GAP-000002"), result.get("productIdentifiers"));
    }

    @Test
    void singleProductFollowUpResolvesFromSameConversationHistory() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Find GAP-000001"),
                Map.of("role", "ASSISTANT", "content", "Found Brake Pad"),
                Map.of("role", "USER", "content", "Show its stock")
        );
        Map<String, Object> result = graphNodes.classifyIntent(
                new ErpAiGraphState(Map.of("currentMessage", "Show its stock", "history", history)),
                RunnableConfig.builder().build());
        assertEquals("PRODUCT_STOCK", result.get("intent"));
        assertEquals("GAP-000001", result.get("searchText"));
    }

    @Test
    void ambiguousFollowUpAfterComparisonAsksWhichProduct() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Compare GAP-000001 and GAP-000002"),
                Map.of("role", "ASSISTANT", "content", "Product comparison..."),
                Map.of("role", "USER", "content", "Show its stock")
        );
        Map<String, Object> result = graphNodes.classifyIntent(
                new ErpAiGraphState(Map.of("currentMessage", "Show its stock", "history", history)),
                RunnableConfig.builder().build());
        assertEquals("CLARIFICATION", result.get("intent"));
        assertTrue(String.valueOf(result.get("clarificationMessage")).contains("more than one product"));
    }

    @Test
    void explicitProductCodeWinsAfterComparison() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Compare GAP-000001 and GAP-000002"),
                Map.of("role", "ASSISTANT", "content", "Product comparison..."),
                Map.of("role", "USER", "content", "Show GAP-000002 stock")
        );
        Map<String, Object> result = graphNodes.classifyIntent(
                new ErpAiGraphState(Map.of("currentMessage", "Show GAP-000002 stock", "history", history)),
                RunnableConfig.builder().build());
        assertEquals("PRODUCT_STOCK", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
    }

    @Test
    void freshConversationDoesNotReuseAnotherConversationProductContext() {
        Map<String, Object> result = graphNodes.classifyIntent(
                new ErpAiGraphState(Map.of("currentMessage", "Show its stock", "history", List.of())),
                RunnableConfig.builder().build());
        assertEquals("CLARIFICATION", result.get("intent"));
    }

    @Test
    void comparisonResponseReportsRequestedProductThatWasNotFound() {
        List<Map<String, Object>> rows = List.of(Map.of(
                "name", "Brake Pad Front",
                "product_code", "GAP-000001",
                "part_no", "BP-FRONT-001",
                "qty", 51,
                "unit", "PCS",
                "minimum_stock", 10,
                "purchase_price", 100,
                "selling_price", 150));
        Map<String, Object> response = graphNodes.generateResponse(
                new ErpAiGraphState(Map.of(
                        "intent", "PRODUCT_COMPARISON",
                        "toolResult", rows,
                        "toolError", "",
                        "productIdentifiers", List.of("GAP-000001", "GAP-000002"))),
                RunnableConfig.builder().build());
        String text = String.valueOf(response.get("finalResponse"));
        assertTrue(text.contains("GAP-000001"));
        assertTrue(text.contains("Not found: GAP-000002"));
    }

    @Test
    void comparisonExecutionUsesEveryIdentifier() {
        List<String> identifiers = List.of("GAP-000001", "GAP-000002");
        when(productLookupTool.getProductsByIdentifiers(identifiers)).thenReturn(List.of());
        graphNodes.executeProductTool(new ErpAiGraphState(Map.of(
                "intent", "PRODUCT_COMPARISON",
                "productIdentifiers", identifiers)), RunnableConfig.builder().build());
        verify(productLookupTool).getProductsByIdentifiers(identifiers);
    }
}
