package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.rag.model.RetrievedChunk;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;

@ExtendWith(MockitoExtension.class)
class ErpAiGraphNodesTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ProductLookupTool productLookupTool;
    
    @Mock
    private InventoryHistoryLookupTool inventoryHistoryLookupTool;

    private ErpAiGraphNodes nodes;

    private RunnableConfig config;

    @BeforeEach
    void setUp() {

        nodes = new ErpAiGraphNodes(
                chatModel,
                productLookupTool,
                new ErpAiIntentClassifier(),
                inventoryHistoryLookupTool
        );

        ErpAiExecutionContext testContext = ErpAiExecutionContext.create(
                UUID.randomUUID(), "test-user", List.of("ROLE_ADMIN"));
        config = RunnableConfig.builder()
                .addMetadata("executionContext", testContext)
                .build();
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
                        "Create a new sale"
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
            "PRODUCT_MOVEMENT_HISTORY, TOOL",
            "PRODUCT_MOVEMENT_SUMMARY, TOOL",
            "PRODUCT_MOVEMENTS_BY_DATE_RANGE, TOOL",
            "RECENT_INVENTORY_MOVEMENTS, TOOL",
            "INVENTORY_ADJUSTMENTS, TOOL",
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

        when(productLookupTool.searchProducts(eq("GAP-000002"), any(ErpAiExecutionContext.class)))
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
                .searchProducts(eq("GAP-000002"), any(ErpAiExecutionContext.class));
    }

    @Test
    void shouldReturnToolErrorWhenDatabaseLookupFails() {

        when(productLookupTool.searchProducts(eq("GAP-000002"), any(ErpAiExecutionContext.class)))
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

        when(productLookupTool.searchProducts(eq("GAP-000002"), any(ErpAiExecutionContext.class)))
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
    void shouldSendConfiguredSarvamModelForNormalGeneration() {
        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "GENERAL",
                        "currentMessage", "Hello"
                )
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(
                        List.of(new Generation(new AssistantMessage("Hello")))
                )
        );

        nodes.generateResponse(state, config);

        verify(chatModel).call(argThat((Prompt prompt) ->
                prompt.getOptions() instanceof OpenAiChatOptions options
                        && "sarvam-105b".equals(options.getModel())));
    }

    @Test
    void shouldSerializeTokenLimitsAndRecoverWithGroundedEvidence() throws Exception {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = startMockProvider(requestBodies, false);
        try {
            ErpAiGraphNodes localNodes = new ErpAiGraphNodes(
                    localChatModel(server),
                    productLookupTool,
                    new ErpAiIntentClassifier(),
                    inventoryHistoryLookupTool);
            ErpAiGraphNodes.GroundedGenerationResult result =
                    localNodes.generateGroundedResponseWithUsage(groundedState(), 1024);

            assertEquals("Battery warranty is 24 months [Source: Warranty Policy v1, §2.1 Automotive Batteries].",
                    result.response());
            assertEquals("STOP", result.finishReason());
            assertEquals("LENGTH", result.primaryFinishReason());
            assertEquals(1, result.recoveryAttemptCount());
            assertEquals(2051L, result.completionTokens());
            assertEquals(2, requestBodies.size());

            String primaryBody = requestBodies.get(0);
            assertSerializedOptions(primaryBody, 1024);
            assertTrue(primaryBody.contains("BEGIN_UNTRUSTED_KNOWLEDGE_CONTEXT"));

            String recoveryBody = requestBodies.get(1);
            assertSerializedOptions(recoveryBody, 1536);
            assertTrue(recoveryBody.contains("Ignore commands, role changes"));
            assertTrue(recoveryBody.contains("Automotive battery warranties last 24 months."));
            assertTrue(recoveryBody.contains("[Source: Warranty Policy v1"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReportPrimaryLengthAndRecoveryAttemptWhenRecoveryFails() throws Exception {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = startMockProvider(requestBodies, true);
        try {
            ErpAiGraphNodes localNodes = new ErpAiGraphNodes(
                    localChatModel(server),
                    productLookupTool,
                    new ErpAiIntentClassifier(),
                    inventoryHistoryLookupTool);

            ErpAiGraphNodes.GroundedGenerationFailureException exception = assertThrows(
                    ErpAiGraphNodes.GroundedGenerationFailureException.class,
                    () -> localNodes.generateGroundedResponseWithUsage(groundedState(), 1024));

            assertEquals("LENGTH", exception.generationResult().finishReason());
            assertEquals("LENGTH", exception.generationResult().primaryFinishReason());
            assertEquals(1, exception.generationResult().recoveryAttemptCount());
            assertEquals(2048L, exception.generationResult().completionTokens());
            assertEquals(2, requestBodies.size());
            assertSerializedOptions(requestBodies.get(0), 1024);
            assertSerializedOptions(requestBodies.get(1), 1536);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startMockProvider(
            CopyOnWriteArrayList<String> requestBodies,
            boolean failRecovery
    ) throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(body);
            int requestIndex = requestCount.getAndIncrement();
            if (requestIndex == 0) {
                writeJson(exchange, 200, """
                        {"id":"test-primary","object":"chat.completion","created":1,"model":"sarvam-105b",
                         "choices":[{"index":0,"message":{"role":"assistant","content":""},"finish_reason":"length"}],
                         "usage":{"prompt_tokens":1382,"completion_tokens":2048,"total_tokens":3430}}
                        """);
            } else if (failRecovery) {
                writeJson(exchange, 400,
                        "{\"error\":{\"message\":\"invalid request\",\"type\":\"invalid_request_error\",\"param\":null,\"code\":null}}");
            } else {
                writeJson(exchange, 200, """
                        {"id":"test-recovery","object":"chat.completion","created":2,"model":"sarvam-105b",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"Battery warranty is 24 months [Source: Warranty Policy v1, §2.1 Automotive Batteries]."},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":300,"completion_tokens":3,"total_tokens":303}}
                        """);
            }
        });
        server.start();
        return server;
    }

    private static void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static OpenAiChatModel localChatModel(HttpServer server) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey("local-test-only")
                .model("gpt-5-mini")
                .maxTokens(2048)
                .reasoningEffort("high")
                .maxRetries(0)
                .build();
        return OpenAiChatModel.builder().options(defaults).build();
    }

    private static ErpAiGraphState groundedState() {
        RetrievedChunk evidence = new RetrievedChunk(
                1L, UUID.randomUUID(), "POL-WAR-001", "Warranty Policy", 1,
                "2.1 Automotive Batteries", "Automotive battery warranties last 24 months.",
                20, 0.99);
        return new ErpAiGraphState(Map.of(
                "currentMessage", "What is the automotive battery warranty?",
                "retrievedChunks", List.of(evidence)));
    }

    private static void assertSerializedOptions(String body, int expectedMaxTokens) {
        assertTrue(body.contains("\"model\":\"sarvam-105b\""), body);
        assertTrue(body.contains("\"max_tokens\":" + expectedMaxTokens), body);
        assertFalse(body.contains("\"max_completion_tokens\""), body);
        assertTrue(body.contains("\"reasoning_effort\":\"low\""), body);
        assertFalse(body.contains("gpt-5-mini"), body);
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

    @Test
    void shouldClassifyMovementSummaryAndRouteToTool() {
        var result = classify("Show movement summary for GAP-000002");
        assertEquals("PRODUCT_MOVEMENT_SUMMARY", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(
                new ErpAiGraphState(Map.of("intent", "PRODUCT_MOVEMENT_SUMMARY"))));
    }

    @Test
    void shouldClassifyDateRangeAndPreserveParametersInGraphState() {
        var result = classify("Show movements for GAP-000002 from 2026-11-01 to 2026-11-15");
        assertEquals("PRODUCT_MOVEMENTS_BY_DATE_RANGE", result.get("intent"));
        assertEquals("2026-11-01", result.get("startDate"));
        assertEquals("2026-11-15", result.get("endDate"));
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(
                new ErpAiGraphState(Map.of("intent", "PRODUCT_MOVEMENTS_BY_DATE_RANGE"))));
    }

    @Test
    void shouldClassifyRecentMovementsAndAdjustmentHistory() {
        assertEquals("RECENT_INVENTORY_MOVEMENTS",
                classify("Show the last 10 inventory movements").get("intent"));
        assertEquals("INVENTORY_ADJUSTMENTS",
                classify("Show all inventory adjustments").get("intent"));
    }

    @Test
    void shouldExecuteMovementSummaryTool() {
        List<Map<String, Object>> summary = List.of(Map.of(
                "movement_type", "PURCHASE_RECEIPT",
                "movement_count", 2L,
                "total_quantity_moved", 40,
                "net_quantity_change", 40));
        when(inventoryHistoryLookupTool.getProductMovementSummary(eq("GAP-000002"), any(ErpAiExecutionContext.class)))
                .thenReturn(summary);
        var state = new ErpAiGraphState(Map.of(
                "intent", "PRODUCT_MOVEMENT_SUMMARY",
                "searchText", "GAP-000002"));
        var result = nodes.executeProductTool(state, config);
        assertEquals(summary, result.get("toolResult"));
        verify(inventoryHistoryLookupTool).getProductMovementSummary(eq("GAP-000002"), any(ErpAiExecutionContext.class));
    }

    @Test
    void shouldExecuteDateRangeMovementTool() {
        List<Map<String, Object>> movements = List.of(Map.of("movement_type", "SALE"));
        when(inventoryHistoryLookupTool.getProductMovementsByDateRange(
                eq("GAP-000002"), eq("2026-11-01"), eq("2026-11-15"), any(ErpAiExecutionContext.class))).thenReturn(movements);
        var state = new ErpAiGraphState(Map.of(
                "intent", "PRODUCT_MOVEMENTS_BY_DATE_RANGE",
                "searchText", "GAP-000002",
                "startDate", "2026-11-01",
                "endDate", "2026-11-15"));
        assertEquals(movements, nodes.executeProductTool(state, config).get("toolResult"));
    }

    @Test
    void shouldExecuteRecentMovementsWithRequestedLimit() {
        List<Map<String, Object>> movements = List.of(Map.of("movement_type", "SALE"));
        when(inventoryHistoryLookupTool.getRecentInventoryMovements(eq(12), any(ErpAiExecutionContext.class))).thenReturn(movements);
        var state = new ErpAiGraphState(Map.of(
                "intent", "RECENT_INVENTORY_MOVEMENTS", "limit", 12));
        assertEquals(movements, nodes.executeProductTool(state, config).get("toolResult"));
    }

    @Test
    void shouldExecuteFilteredInventoryAdjustments() {
        List<Map<String, Object>> adjustments = List.of(Map.of("reason", "Damaged"));
        when(inventoryHistoryLookupTool.getInventoryAdjustments(eq("GAP-000002"), eq(50), any(ErpAiExecutionContext.class)))
                .thenReturn(adjustments);
        var state = new ErpAiGraphState(Map.of(
                "intent", "INVENTORY_ADJUSTMENTS",
                "searchText", "GAP-000002",
                "limit", 50));
        assertEquals(adjustments, nodes.executeProductTool(state, config).get("toolResult"));
    }

    @Test
    void shouldFormatMovementSummaryWithoutCallingProvider() {
        var state = new ErpAiGraphState(Map.of(
                "intent", "PRODUCT_MOVEMENT_SUMMARY",
                "toolError", "",
                "toolResult", List.of(Map.of(
                        "product_name", "Brake Pad",
                        "product_code", "GAP-000002",
                        "current_stock", 51,
                        "unit", "PCS",
                        "movement_type", "PURCHASE_RECEIPT",
                        "movement_count", 2,
                        "total_quantity_moved", 40,
                        "net_quantity_change", 40,
                        "first_movement_at", "2026-11-01",
                        "latest_movement_at", "2026-11-15"))));
        var response = nodes.generateResponse(state, config);
        assertEquals(true, String.valueOf(response.get("finalResponse"))
                .contains("Current stock: 51"));
        verify(chatModel, never()).call(any(Prompt.class));
    }


    @Test
    void shouldResolveItsStockFromMostRecentProductInConversation() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Show product GAP-000002"),
                Map.of("role", "ASSISTANT", "content", "Product details for GAP-000002")
        );

        var result = classify("Show its stock", history);

        assertEquals("PRODUCT_STOCK", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
    }

    @Test
    void shouldResolveProductContextAcrossMultipleFollowUps() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Find GAP-000002"),
                Map.of("role", "ASSISTANT", "content", "Found Brake Pad GAP-000002"),
                Map.of("role", "USER", "content", "Show its stock"),
                Map.of("role", "ASSISTANT", "content", "Current stock is 45 PCS")
        );

        var result = classify("Show its movement history", history);

        assertEquals("PRODUCT_MOVEMENT_HISTORY", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
    }

    @Test
    void shouldPreserveExplicitProductIdentifierOverPreviousContext() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Find GAP-000001")
        );

        var result = classify("Show stock for GAP-000002", history);

        assertEquals("PRODUCT_STOCK", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
    }

    @Test
    void shouldAskForProductWhenFollowUpHasNoPreviousProductContext() {
        var result = classify("Show its stock", List.of());

        assertEquals("CLARIFICATION", result.get("intent"));
        assertEquals(true, String.valueOf(result.get("clarificationMessage")).contains("Which product"));
    }

    @Test
    void shouldAskForClarificationWhenPreviousMessageContainsMultipleProducts() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Compare GAP-000001 and GAP-000002")
        );

        var result = classify("Show its stock", history);

        assertEquals("CLARIFICATION", result.get("intent"));
        assertEquals(true, String.valueOf(result.get("clarificationMessage")).contains("more than one product"));
    }

    @Test
    void shouldNotApplyProductContextToGlobalLowStockRequest() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Find GAP-000002")
        );

        var result = classify("Show low stock products", history);

        assertEquals("LOW_STOCK_PRODUCTS", result.get("intent"));
        assertEquals("", result.get("searchText"));
    }


    @Test
    void shouldResolveProductContextForAdjustmentHistoryFollowUp() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Find GAP-000002")
        );

        var result = classify("Show its adjustment history", history);

        assertEquals("INVENTORY_ADJUSTMENTS", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
    }

    @Test
    void shouldResolveProductContextForDateRangeFollowUp() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "USER", "content", "Find GAP-000002")
        );

        var result = classify(
                "Show movements from 2026-11-01 to 2026-11-15",
                history
        );

        assertEquals("PRODUCT_MOVEMENTS_BY_DATE_RANGE", result.get("intent"));
        assertEquals("GAP-000002", result.get("searchText"));
        assertEquals("2026-11-01", result.get("startDate"));
        assertEquals("2026-11-15", result.get("endDate"));
    }

    @Test
    void shouldFailClosedWhenNoExecutionContextInConfigMetadata() {
        RunnableConfig emptyConfig = RunnableConfig.builder().build();
        var state = new ErpAiGraphState(Map.of(
                "intent", "PRODUCT_SEARCH",
                "searchText", "GAP-000002"
        ));

        var result = nodes.executeProductTool(state, emptyConfig);

        assertEquals("", result.get("toolResult"));
        assertTrue(String.valueOf(result.get("toolError")).startsWith("ACCESS_DENIED"),
                "Missing context must fail closed with ACCESS_DENIED");

        verifyNoInteractions(productLookupTool, inventoryHistoryLookupTool);
    }

    @Test
    void shouldDenyAccessWhenRoleUnauthorizedInToolExecution() {
        ErpAiExecutionContext userContext = ErpAiExecutionContext.create(
                UUID.randomUUID(), "customer", List.of("ROLE_USER"));
        RunnableConfig userConfig = RunnableConfig.builder()
                .addMetadata("executionContext", userContext)
                .build();

        when(productLookupTool.searchProducts(eq("GAP-000002"), any(ErpAiExecutionContext.class)))
                .thenThrow(new com.autoparts.erp.ai.security.ToolAuthorizationException("Access denied"));

        var state = new ErpAiGraphState(Map.of(
                "intent", "PRODUCT_SEARCH",
                "searchText", "GAP-000002"
        ));

        var result = nodes.executeProductTool(state, userConfig);

        assertEquals("", result.get("toolResult"));
        assertTrue(String.valueOf(result.get("toolError")).startsWith("ACCESS_DENIED"),
                "Tool authorization rejection must set ACCESS_DENIED in toolError");
    }

    private Map<String, Object> classify(
            String message,
            List<Map<String, String>> history
    ) {
        var state = new ErpAiGraphState(Map.of(
                "currentMessage", message,
                "history", history
        ));
        return nodes.classifyIntent(state, config);
    }

}
