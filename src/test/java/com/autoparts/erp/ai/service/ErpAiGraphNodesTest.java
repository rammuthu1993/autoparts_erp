
package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.tools.ProductLookupTool;

@ExtendWith(MockitoExtension.class)
class ErpAiGraphNodesTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ProductLookupTool productLookupTool;

    private ErpAiGraphNodes nodes;

    private RunnableConfig config;

    @BeforeEach
    void setUp() {
        nodes = new ErpAiGraphNodes(
                chatModel,
                productLookupTool,
                new ErpAiIntentClassifier()
        );

        config = RunnableConfig.builder().build();
    }

    @Test
    void shouldClassifyProductSearch() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "Find product GAP-000002"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "PRODUCT_SEARCH",
                result.get("intent")
        );

        assertEquals(
                "GAP-000002",
                result.get("searchText")
        );
    }

    @Test
    void shouldClassifyProductStock() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "How much stock is available for GAP-000002?"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "PRODUCT_STOCK",
                result.get("intent")
        );
    }

    @Test
    void shouldClassifyProductDetails() {
        var result = classify("Show details for GAP-000002");

        assertEquals("PRODUCT_DETAILS", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
    }

    @Test
    void shouldClassifyGeneralQuestion() {
        assertEquals(
                "GENERAL",
                classify("Explain how product categories work.").get("intent")
        );
    }

    @Test
    void shouldClassifyProductCount() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "How many products are available?"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "COUNT_PRODUCTS",
                result.get("intent")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "How many active products are available?",
            "How many products are active?",
            "Count of active products",
            "What is the total active product count?",
            "How many products do we have?"
    })
    void shouldClassifyDifferentProductCountPhrasings(String message) {
        var result = classify(message);

        assertEquals("COUNT_PRODUCTS", result.get("intent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Which products are running low?",
            "Show items that need restocking",
            "List products below minimum stock"
    })
    void shouldClassifyDifferentLowStockPhrasings(String message) {
        var result = classify(message);

        assertEquals("LOW_STOCK_PRODUCTS", result.get("intent"));
    }

    @Test
    void shouldClassifyProductSearchByName() {
        var result = classify("Show me brake pads");

        assertEquals("PRODUCT_SEARCH", result.get("intent"));
        assertEquals("brake pads", result.get("searchText"));
    }

    @Test
    void shouldExtractUnhyphenatedProductCodeWithoutTreatingNumericIdsAsCodes() {
        var result = classify("Find product PRD001");
        var numericResult = classify("Find product 123");

        assertEquals("PRODUCT_SEARCH", result.get("intent"));
        assertEquals("PRD001", result.get("searchText"));
        assertEquals("123", numericResult.get("searchText"));
    }

    @Test
    void shouldClarifyProductRequestsWithoutSearchTerms() {
        assertEquals("CLARIFICATION", classify("Find product").get("intent"));
        assertEquals("CLARIFICATION", classify("What is the stock?").get("intent"));
    }

    @Test
    void shouldRejectInvalidIntentAndRouteInvalidStateSafely() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ErpAiIntentResult("INVALID", "")
        );

        var state = new ErpAiGraphState(Map.of("intent", "INVALID"));
        assertEquals("CLARIFICATION", ErpAiGraphNodes.routeIntent(state));
    }

    @Test
    void shouldClassifyLowStock() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "Show low stock products"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "LOW_STOCK_PRODUCTS",
                result.get("intent")
        );
    }

    @Test
    void shouldClassifyUnsupportedRequest() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "Show supplier details"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "UNSUPPORTED",
                result.get("intent")
        );
    }

    @ParameterizedTest
    @CsvSource({
            "PRODUCT_SEARCH, TOOL",
            "PRODUCT_DETAILS, TOOL",
            "PRODUCT_STOCK, TOOL",
            "COUNT_PRODUCTS, TOOL",
            "LOW_STOCK_PRODUCTS, TOOL",
            "GENERAL, GENERAL",
            "UNSUPPORTED, UNSUPPORTED",
            "CLARIFICATION, CLARIFICATION",
            "INVALID, CLARIFICATION"
    })
    void shouldRouteSupportedAndInvalidIntentsSafely(
            String intent,
            String expectedRoute
    ) {
        var state = new ErpAiGraphState(Map.of("intent", intent));

        assertEquals(expectedRoute, ErpAiGraphNodes.routeIntent(state));
    }

    @Test
    void shouldExecuteProductSearchTool() {

        List<Map<String, Object>> products = List.of(
                Map.of(
                        "product_code", "GAP-000002",
                        "name", "Brake Pad (Rear)",
                        "qty", 31
                )
        );

        when(productLookupTool.searchProducts("GAP-000002"))
                .thenReturn(products);

        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "PRODUCT_SEARCH",
                        "searchText", "GAP-000002"
                )
        );

        var result = nodes.executeProductTool(state, config);

        assertEquals(
                products,
                result.get("toolResult")
        );

        assertEquals(
                "",
                result.get("toolError")
        );

        verify(productLookupTool)
                .searchProducts("GAP-000002");
    }

    @Test
    void shouldReturnToolErrorWhenDatabaseLookupFails() {

        when(productLookupTool.searchProducts("GAP-000002"))
                .thenThrow(
                        new IllegalStateException("Database unavailable")
                );

        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "PRODUCT_SEARCH",
                        "searchText", "GAP-000002"
                )
        );

        var result = nodes.executeProductTool(state, config);

        assertEquals(
                "Unable to retrieve the requested ERP information.",
                result.get("toolError")
        );
    }

    @Test
    void shouldPropagateCancellation() {

        when(productLookupTool.searchProducts("GAP-000002"))
                .thenThrow(
                        new CancellationException("Cancelled")
                );

        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "PRODUCT_SEARCH",
                        "searchText", "GAP-000002"
                )
        );

        assertThrows(
                CancellationException.class,
                () -> nodes.executeProductTool(state, config)
        );
    }

    @Test
    void shouldReturnProductCountWithoutCallingAiProvider() {
        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "COUNT_PRODUCTS",
                        "toolResult", 125L,
                        "toolError", "",
                        "currentMessage",
                        "How many active products are available?"
                )
        );

        var result = nodes.generateResponse(state, config);

        assertEquals(
                "There are 125 active products in the ERP system.",
                result.get("finalResponse")
        );
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldRejectNullProviderResponse() {
        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "GENERAL",
                        "currentMessage", "Hello"
                )
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        assertThrows(
                AiProviderResponseException.class,
                () -> nodes.generateResponse(state, config)
        );
    }

    @Test
    void shouldRejectProviderResponseWithoutText() {
        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "GENERAL",
                        "currentMessage", "Hello"
                )
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(
                        List.of(new Generation(new AssistantMessage("")))
                )
        );

        assertThrows(
                AiProviderResponseException.class,
                () -> nodes.generateResponse(state, config)
        );
    }

    private Map<String, Object> classify(String message) {
        var state = new ErpAiGraphState(
                Map.of("currentMessage", message)
        );
        return nodes.classifyIntent(state, config);
    }
}
