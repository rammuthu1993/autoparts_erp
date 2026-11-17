package com.autoparts.erp.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import com.autoparts.erp.ai.enums.ClassificationSource;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.tools.AuditLogLookupTool;
import com.autoparts.erp.ai.tools.CustomerLookupTool;
import com.autoparts.erp.ai.tools.FinancialLookupTool;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.autoparts.erp.ai.tools.PurchaseLookupTool;
import com.autoparts.erp.ai.tools.ReportLookupTool;
import com.autoparts.erp.ai.tools.ReturnLookupTool;
import com.autoparts.erp.ai.tools.SalesLookupTool;
import com.autoparts.erp.ai.tools.SupplierLookupTool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Stage2EGraphRoutingIntegrationTest {

    private ChatModel chatModel;
    private ProductLookupTool productLookupTool;
    private InventoryHistoryLookupTool inventoryHistoryLookupTool;
    private PurchaseLookupTool purchaseLookupTool;
    private SalesLookupTool salesLookupTool;
    private CustomerLookupTool customerLookupTool;
    private SupplierLookupTool supplierLookupTool;
    private ReturnLookupTool returnLookupTool;
    private FinancialLookupTool financialLookupTool;
    private ReportLookupTool reportLookupTool;
    private AuditLogLookupTool auditLogLookupTool;

    private ErpAiIntentClassifier intentClassifier;
    private ErpAiPendingClarificationStore clarificationStore;
    private ErpAiLlmClassifier llmClassifier;
    private ErpAiHybridIntentRouter hybridRouter;
    private ErpAiGraphNodes nodes;
    private ErpAiAgentService agentService;

    private final UUID conversationId = UUID.randomUUID();
    private final ErpAiExecutionContext adminContext =
            ErpAiExecutionContext.create(conversationId, "admin-user", Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER"));

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        productLookupTool = mock(ProductLookupTool.class);
        inventoryHistoryLookupTool = mock(InventoryHistoryLookupTool.class);
        purchaseLookupTool = mock(PurchaseLookupTool.class);
        salesLookupTool = mock(SalesLookupTool.class);
        customerLookupTool = mock(CustomerLookupTool.class);
        supplierLookupTool = mock(SupplierLookupTool.class);
        returnLookupTool = mock(ReturnLookupTool.class);
        financialLookupTool = mock(FinancialLookupTool.class);
        reportLookupTool = mock(ReportLookupTool.class);
        auditLogLookupTool = mock(AuditLogLookupTool.class);

        intentClassifier = new ErpAiIntentClassifier();
        clarificationStore = new ErpAiPendingClarificationStore();
        llmClassifier = mock(ErpAiLlmClassifier.class);

        hybridRouter = new ErpAiHybridIntentRouter(intentClassifier, clarificationStore, llmClassifier);

        nodes = new ErpAiGraphNodes(
                chatModel, productLookupTool, intentClassifier, inventoryHistoryLookupTool,
                purchaseLookupTool, salesLookupTool, customerLookupTool, supplierLookupTool,
                returnLookupTool, financialLookupTool, reportLookupTool, auditLogLookupTool,
                hybridRouter
        );

        agentService = new ErpAiAgentService(nodes, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        if (agentService != null) {
            agentService.shutdownTimeoutScheduler();
        }
    }

    private RunnableConfig makeConfig(ErpAiExecutionContext context) {
        return RunnableConfig.builder()
                .addMetadata("executionContext", context)
                .build();
    }

    @Test
    @DisplayName("Graph classify node must invoke ErpAiHybridIntentRouter and route LLM-resolved intent to TOOL")
    void shouldRouteLlmFallbackIntentToTool() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.of(
                new ErpAiIntentResult("PURCHASES_BY_SUPPLIER", "Bosch", "", "", 10, "", List.of(),
                        0.95, ClassificationSource.LLM_FALLBACK, false, "", "")
        ));
        when(purchaseLookupTool.getPurchasesBySupplier(eq("Bosch"), any())).thenReturn(List.of(
                Map.of("purchase_number", "PUR-1001", "supplier_name", "Bosch", "grand_total", 5000.0)
        ));

        ErpAiGraphState initState = new ErpAiGraphState(Map.of(
                "conversationId", conversationId.toString(),
                "currentMessage", "What did we buy from Bosch recently?",
                "history", List.of()
        ));

        // 1. Classify intent via graph node
        Map<String, Object> classified = nodes.classifyIntent(initState, makeConfig(adminContext));
        ErpAiGraphState classifiedState = new ErpAiGraphState(classified);

        assertEquals("PURCHASES_BY_SUPPLIER", classifiedState.intent());
        assertEquals("LLM_FALLBACK", classifiedState.source());
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(classifiedState));

        // 2. Execute tool via graph node
        Map<String, Object> toolResult = nodes.executeProductTool(classifiedState, makeConfig(adminContext));
        verify(purchaseLookupTool).getPurchasesBySupplier(eq("Bosch"), eq(adminContext));
        assertTrue(toolResult.get("toolError").toString().isEmpty());
    }

    @Test
    @DisplayName("Graph clarification response must never execute any JDBC tools")
    void shouldNotExecuteToolsOnClarification() {
        ErpAiGraphState initState = new ErpAiGraphState(Map.of(
                "conversationId", conversationId.toString(),
                "currentMessage", "daily closing",
                "history", List.of()
        ));

        Map<String, Object> classified = nodes.classifyIntent(initState, makeConfig(adminContext));
        ErpAiGraphState classifiedState = new ErpAiGraphState(classified);

        assertEquals("CLARIFICATION", classifiedState.intent());
        assertEquals("CLARIFICATION", ErpAiGraphNodes.routeIntent(classifiedState));

        // Generate clarification response
        Map<String, Object> response = nodes.clarificationResponse(classifiedState, makeConfig(adminContext));
        assertTrue(response.get("finalResponse").toString().contains("YYYY-MM-DD"));

        // Verify zero tools were called
        verify(financialLookupTool, never()).getDailyClosing(anyString(), any());
        verify(productLookupTool, never()).searchProducts(anyString(), any());
    }

    @Test
    @DisplayName("Compound query containing a mutation must route to UNSUPPORTED and never execute tools")
    void shouldRejectCompoundMutationWithoutCallingTools() {
        ErpAiGraphState initState = new ErpAiGraphState(Map.of(
                "conversationId", conversationId.toString(),
                "currentMessage", "Show stock of GAP-000001 and delete product GAP-000002",
                "history", List.of()
        ));

        Map<String, Object> classified = nodes.classifyIntent(initState, makeConfig(adminContext));
        ErpAiGraphState classifiedState = new ErpAiGraphState(classified);

        assertEquals("UNSUPPORTED", classifiedState.intent());
        assertEquals("UNSUPPORTED", ErpAiGraphNodes.routeIntent(classifiedState));

        Map<String, Object> response = nodes.unsupportedResponse(classifiedState, makeConfig(adminContext));
        assertTrue(response.get("finalResponse").toString().contains("not currently available"));

        // Verify zero tools were called
        verify(productLookupTool, never()).getProductStock(anyString(), any());
    }

    @Test
    @DisplayName("Multi-turn clarification resolution across separate graph node calls")
    void shouldResolveMultiTurnClarificationInGraph() {
        when(financialLookupTool.getDailyClosing(eq("2026-11-17"), any())).thenReturn(Map.of(
                "closing_date", "2026-11-17",
                "status", "BALANCED",
                "total_sales", 15000.0
        ));

        // Turn 1: User asks for daily closing
        ErpAiGraphState turn1Init = new ErpAiGraphState(Map.of(
                "conversationId", conversationId.toString(),
                "currentMessage", "daily closing report",
                "history", List.of()
        ));
        Map<String, Object> turn1Classified = nodes.classifyIntent(turn1Init, makeConfig(adminContext));
        assertEquals("CLARIFICATION", turn1Classified.get("intent"));

        // Turn 2: Follow-up message with date on the same conversation
        ErpAiGraphState turn2Init = new ErpAiGraphState(Map.of(
                "conversationId", conversationId.toString(),
                "currentMessage", "2026-11-17",
                "history", List.of(
                        Map.of("role", "USER", "content", "daily closing report"),
                        Map.of("role", "ASSISTANT", "content", "Please provide the business closing date in YYYY-MM-DD format.")
                )
        ));

        Map<String, Object> turn2Classified = nodes.classifyIntent(turn2Init, makeConfig(adminContext));
        ErpAiGraphState turn2State = new ErpAiGraphState(turn2Classified);

        assertEquals("DAILY_CLOSING", turn2State.intent());
        assertEquals("2026-11-17", turn2State.startDate());
        assertEquals("CONTEXT_RESOLVED", turn2State.source());
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(turn2State));

        // Execute tool for resolved intent
        Map<String, Object> toolResult = nodes.executeProductTool(turn2State, makeConfig(adminContext));
        verify(financialLookupTool).getDailyClosing(eq("2026-11-17"), eq(adminContext));
    }

    @Test
    @DisplayName("End-to-End Compiled Graph Workflow: Colloquial query routes through LLM fallback to tool node and formats response")
    void shouldExecuteEndToEndCompiledWorkflowForColloquialQuery() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.of(
                new ErpAiIntentResult("PURCHASES_BY_SUPPLIER", "Bosch", "", "", 10, "", List.of(),
                        0.95, ClassificationSource.LLM_FALLBACK, false, "", "")
        ));
        when(purchaseLookupTool.getPurchasesBySupplier(eq("Bosch"), any())).thenReturn(List.of(
                Map.of(
                        "purchase_number", "PUR-1001",
                        "supplier_name", "Bosch",
                        "supplier_invoice_no", "SINV-001",
                        "purchase_date", "2026-11-10",
                        "status", "RECEIVED",
                        "grand_total", 5000.0
                )
        ));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can you check what products we bought from Bosch recently?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("PUR-1001"));
        assertTrue(response.contains("Bosch"));
        verify(purchaseLookupTool).getPurchasesBySupplier(eq("Bosch"), eq(adminContext));
    }

    @Test
    @DisplayName("End-to-End Compiled Graph Workflow: Clarification in compiled workflow never calls database tools")
    void shouldNotExecuteToolsInCompiledWorkflowOnClarification() {
        UUID clarConvId = UUID.randomUUID();
        String response = agentService.chat(
                clarConvId,
                UUID.randomUUID(),
                List.of(new UserMessage("daily closing")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("YYYY-MM-DD"));
        verify(financialLookupTool, never()).getDailyClosing(anyString(), any());
    }

    @Test
    @DisplayName("End-to-End Compiled Graph Workflow: Missing execution context fails closed without tool execution")
    void shouldFailClosedInCompiledWorkflowWhenExecutionContextMissing() {
        UUID noAuthConvId = UUID.randomUUID();
        String response = agentService.chat(
                noAuthConvId,
                UUID.randomUUID(),
                List.of(new UserMessage("daily closing 2026-11-17")),
                null
        );

        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("access denied") || response.toLowerCase().contains("security"));
        verify(financialLookupTool, never()).getDailyClosing(anyString(), any());
    }
}
