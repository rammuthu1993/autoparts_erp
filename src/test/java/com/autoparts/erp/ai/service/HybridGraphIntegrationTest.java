package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import com.autoparts.erp.ai.rag.citation.CitationValidator;
import com.autoparts.erp.ai.rag.model.RetrievedChunk;
import com.autoparts.erp.ai.rag.retrieval.KnowledgeRetrievalException;
import com.autoparts.erp.ai.rag.retrieval.KnowledgeRetrievalService;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;
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

/**
 * End-to-end graph-level hybrid ERP + RAG integration tests verifying:
 * 1. Intent classification and hybrid routing.
 * 2. Correct ERP tool selection and execution.
 * 3. Authoritative RAG retrieval and effective date / approval enforcement.
 * 4. Context preservation (ERP transaction context + Knowledge context).
 * 5. Prompt evidence construction without self-contradicting instructions.
 * 6. Citation validation (treating ERP facts appropriately without requiring document citations).
 * 7. Fail-closed authorization and partial evidence handling across scenarios A through H.
 */
class HybridGraphIntegrationTest {

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

    private KnowledgeRetrievalService knowledgeRetrievalService;
    private CitationValidator citationValidator;

    private ErpAiIntentClassifier intentClassifier;
    private ErpAiPendingClarificationStore clarificationStore;
    private ErpAiHybridIntentRouter hybridRouter;
    private ErpAiGraphNodes nodes;
    private ErpAiAgentService agentService;

    private final UUID conversationId = UUID.randomUUID();
    private final ErpAiExecutionContext adminContext =
            ErpAiExecutionContext.create(conversationId, "admin_user", Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER"));
    private final ErpAiExecutionContext cashierContext =
            ErpAiExecutionContext.create(conversationId, "cashier_user", Set.of("ROLE_CASHIER"));

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

        knowledgeRetrievalService = mock(KnowledgeRetrievalService.class);
        citationValidator = new CitationValidator();

        intentClassifier = new ErpAiIntentClassifier();
        clarificationStore = new ErpAiPendingClarificationStore();
        hybridRouter = new ErpAiHybridIntentRouter(intentClassifier, clarificationStore, null);

        nodes = new ErpAiGraphNodes(
                chatModel,
                productLookupTool,
                intentClassifier,
                inventoryHistoryLookupTool,
                purchaseLookupTool,
                salesLookupTool,
                customerLookupTool,
                supplierLookupTool,
                returnLookupTool,
                financialLookupTool,
                reportLookupTool,
                auditLogLookupTool,
                hybridRouter,
                knowledgeRetrievalService,
                citationValidator
        );

        agentService = new ErpAiAgentService(nodes, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        if (agentService != null) {
            agentService.shutdownTimeoutScheduler();
        }
    }

    // ----------------------------------------------------------------------------------
    // Scenario A: Product + return policy ("Can I return GAP-000001 purchased last week?")
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario A: Product return inquiry executes purchase tool, RAG, and combines live facts with policy citations")
    void scenarioA_productAndReturnPolicy() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "supplier_invoice_no", "SUP-INV-101",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "supplier_invoice_no", "SUP-INV-101",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));

        RetrievedChunk returnChunk = new RetrievedChunk(
                1L, UUID.randomUUID(), "POL-RET-01", "Return Policy", 1,
                "Eligibility", "Customers have 30 days from purchase to return unopened parts.", 30, 0.95
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of(returnChunk));

        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn(
                "You purchased GAP-000001 on 2026-11-12 under invoice PUR-0001. Under company policy, eligible items can be returned within 30 days [Source: Return Policy v1, §Eligibility]."
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("PUR-0001"));
        assertTrue(response.contains("2026-11-12"));
        assertTrue(response.contains("[Source: Return Policy v1, §Eligibility]"));

        verify(knowledgeRetrievalService).retrieveKnowledge(anyString(), eq(adminContext), eq(4));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String fullPrompt = promptCaptor.getValue().getContents();
        assertTrue(fullPrompt.contains("<ERP_TRANSACTION_CONTEXT>"));
        assertTrue(fullPrompt.contains("<KNOWLEDGE_CONTEXT>"));
        assertTrue(fullPrompt.contains("PUR-0001"));
        assertFalse(fullPrompt.contains("Answer the user's question using ONLY the provided KNOWLEDGE_CONTEXT"));
    }

    // ----------------------------------------------------------------------------------
    // Scenario B: Product + warranty ("What is the warranty for GAP-000001 and when did I purchase it?")
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario B: Product warranty inquiry executes purchase tool, RAG, and combines live facts with warranty citations")
    void scenarioB_productAndWarranty() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0002",
                        "purchase_date", "2026-05-10",
                        "grand_total", 320.00
                )));

        RetrievedChunk warrantyChunk = new RetrievedChunk(
                2L, UUID.randomUUID(), "POL-WAR-01", "Warranty Policy", 1,
                "Coverage", "Alternators have a 24-month manufacturer warranty from purchase date.", 25, 0.95
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of(warrantyChunk));

        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn(
                "GAP-000001 was purchased on 2026-05-10 under PUR-0002. Alternators are covered by a 24-month manufacturer warranty [Source: Warranty Policy v1, §Coverage]."
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("What is the warranty for GAP-000001 and when did I purchase it?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("PUR-0002"));
        assertTrue(response.contains("2026-05-10"));
        assertTrue(response.contains("[Source: Warranty Policy v1, §Coverage]"));

        verify(purchaseLookupTool).searchPurchases("GAP-000001", adminContext);
        verify(knowledgeRetrievalService).retrieveKnowledge(anyString(), eq(adminContext), eq(4));
    }

    // ----------------------------------------------------------------------------------
    // Scenario C: Cash register + closing SOP ("Show today's cash balance and explain the closing procedure.")
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario C: Cash balance and closing procedure executes financial tool, RAG SOP, and combines both")
    void scenarioC_cashBalanceAndClosingProcedure() {
        when(financialLookupTool.getOpenCashRegisterStatus(eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "session_id", 101L,
                        "cashier_username", "alice",
                        "opening_cash", 2000.00,
                        "current_balance", 8500.00
                )));

        RetrievedChunk sopChunk = new RetrievedChunk(
                3L, UUID.randomUUID(), "SOP-CLS-01", "Daily Closing SOP", 1,
                "Procedure", "Count physical notes in the drawer, reconcile card slips, and lock the safe.", 25, 0.95
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of(sopChunk));

        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn(
                "Current cash register balance is 8500.00. The closing procedure requires counting notes and locking the safe [Source: Daily Closing SOP v1, §Procedure]."
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Show today's cash balance and explain the closing procedure.")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("8500"));
        assertTrue(response.contains("[Source: Daily Closing SOP v1, §Procedure]"));

        verify(financialLookupTool).getOpenCashRegisterStatus(adminContext);
        verify(knowledgeRetrievalService).retrieveKnowledge(anyString(), eq(adminContext), eq(4));
    }

    // ----------------------------------------------------------------------------------
    // Scenario C: ERP success + RAG technical failure
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario C: ERP success + RAG technical failure preserves ERP facts and discloses knowledge service error")
    void scenarioC_erpSuccessAndRagTechnicalFailure() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));

        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenThrow(new KnowledgeRetrievalException("Simulated RAG database timeout", new RuntimeException()));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("PUR-0001"));
        assertTrue(response.contains("2026-11-12"));
        assertTrue(response.contains("The policy knowledge base is temporarily unavailable due to a technical error"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ----------------------------------------------------------------------------------
    // Scenario D: ERP success + RAG no evidence
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario D: ERP success with zero RAG chunks preserves ERP facts and states policy is unavailable without calling LLM")
    void scenarioD_erpSuccessAndRagNoEvidence() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));

        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of());

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("PUR-0001"));
        assertTrue(response.contains("2026-11-12"));
        assertTrue(response.toLowerCase().contains("could not find any applicable company policy"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ----------------------------------------------------------------------------------
    // Scenario E: RAG success + ERP failure (e.g. no purchase found)
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario E: RAG success with missing ERP record answers policy with citations and notes ERP data was not found")
    void scenarioE_ragSuccessAndErpFailure() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of());
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of());

        RetrievedChunk returnChunk = new RetrievedChunk(
                1L, UUID.randomUUID(), "POL-RET-01", "Return Policy", 1,
                "Eligibility", "Customers have 30 days from purchase to return eligible parts.", 25, 0.95
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of(returnChunk));

        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn(
                "No purchase record was found for GAP-000001. Under store policy, eligible items can be returned within 30 days [Source: Return Policy v1, §Eligibility]."
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("No purchase record was found"));
        assertTrue(response.contains("[Source: Return Policy v1, §Eligibility]"));

        verify(knowledgeRetrievalService).retrieveKnowledge(anyString(), eq(adminContext), eq(4));
    }

    // ----------------------------------------------------------------------------------
    // Scenario F: ERP authorization failure
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario F: ERP authorization failure fails closed and returns access denied without leaking data")
    void scenarioF_erpAuthorizationFailure() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(cashierContext)))
                .thenThrow(new ToolAuthorizationException("Access denied: Admin or Manager role required."));
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(cashierContext)))
                .thenThrow(new ToolAuthorizationException("Access denied: Admin or Manager role required."));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                cashierContext
        );

        assertNotNull(response);
        assertTrue(response.contains("ACCESS_DENIED") || response.toLowerCase().contains("permission"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ----------------------------------------------------------------------------------
    // Scenario G: RAG authorization failure
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario G: RAG authorization failure fails closed and returns access denied")
    void scenarioG_ragAuthorizationFailure() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(Map.of("purchase_number", "PUR-0001")));
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of(Map.of("purchase_number", "PUR-0001")));

        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenThrow(new ToolAuthorizationException("Access denied: insufficient document role."));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("Access denied") || response.contains("permission"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ----------------------------------------------------------------------------------
    // Scenario H: Both sources unavailable
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario H: Both sources returning empty produces safe error response without calling LLM")
    void scenarioH_bothSourcesUnavailable() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of());
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of());
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of());

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("could not find any matching ERP transaction records")
                || response.toLowerCase().contains("could not find"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // ----------------------------------------------------------------------------------
    // Multiple purchases ambiguity test
    // ----------------------------------------------------------------------------------
    @Test
    @DisplayName("Ambiguity: Multiple purchases found for product asks for clarification")
    void shouldAskClarificationWhenMultiplePurchasesFound() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(
                        Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-01"),
                        Map.of("purchase_number", "PUR-0002", "purchase_date", "2026-11-15")
                ));
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), eq(adminContext)))
                .thenReturn(List.of(
                        Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-01"),
                        Map.of("purchase_number", "PUR-0002", "purchase_date", "2026-11-15")
                ));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("Multiple purchase records were found"));
        assertTrue(response.contains("specify the purchase number"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Disambiguation: Relative date filter narrows multiple purchases down to one")
    void shouldDisambiguateMultiplePurchasesWithDateRange() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(
                        Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-12", "grand_total", 450.00)
                ));

        RetrievedChunk returnChunk = new RetrievedChunk(
                1L, UUID.randomUUID(), "POL-RET-01", "Return Policy", 1,
                "Eligibility", "Customers have 30 days from purchase to return unopened parts.", 30, 0.95
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of(returnChunk));

        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn(
                "You purchased GAP-000001 under PUR-0001 on 2026-11-12. Returns are allowed within 30 days [Source: Return Policy v1, §Eligibility]."
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("PUR-0001"));
        assertFalse(response.contains("Multiple purchase records were found"));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Security: Citation validator rejects fabricated ERP facts in hybrid mode")
    void shouldRejectFabricatedErpFactsInHybridMode() {
        when(purchaseLookupTool.searchPurchases(eq("GAP-000001"), anyString(), anyString(), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date", "2026-11-12",
                        "grand_total", 450.00
                )));

        RetrievedChunk returnChunk = new RetrievedChunk(
                1L, UUID.randomUUID(), "POL-RET-01", "Return Policy", 1,
                "Eligibility", "Customers have 30 days from purchase to return unopened parts.", 30, 0.95
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), eq(adminContext), anyInt()))
                .thenReturn(List.of(returnChunk));

        // Model hallucinates PUR-99999 which is not in the ERP toolResult
        ChatResponse mockChatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockChatResponse.getResult().getOutput().getText()).thenReturn(
                "You purchased GAP-000001 on 2025-01-01 under invoice PUR-99999. Under policy, returns are allowed within 30 days [Source: Return Policy v1, §Eligibility]."
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Can I return GAP-000001 purchased last week?")),
                adminContext
        );

        assertNotNull(response);
        assertFalse(response.contains("PUR-99999"));
        assertFalse(response.contains("2025-01-01"));
        assertTrue(response.contains("[Source: Return Policy v1, §Eligibility]"));
    }
}
