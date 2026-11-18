package com.autoparts.erp.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Stage3KnowledgeGraphIntegrationTest {

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

    @Test
    @DisplayName("Full graph workflow: Knowledge query executes retrieve -> validate context -> generate grounded -> validate citations")
    void shouldExecuteKnowledgeWorkflowWithGroundedCitations() {
        UUID docId = UUID.randomUUID();
        RetrievedChunk chunk = new RetrievedChunk(
                1L,
                docId,
                "POL-RET-01",
                "Return Policy",
                1,
                "Damaged Parts",
                "Damaged parts must be reported within 48 hours of delivery and photographed before credit.",
                35,
                0.95
        );

        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), any(ErpAiExecutionContext.class), anyInt()))
                .thenReturn(List.of(chunk));

        ChatResponse mockResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockResponse.getResult().getOutput().getText()).thenReturn(
                "Damaged parts must be reported within 48 hours with photographs. [Source: Return Policy v1, §Damaged Parts]"
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("What is the company policy for damaged parts?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("48 hours"));
        assertTrue(response.contains("[Source: Return Policy v1, §Damaged Parts]"));

        verify(knowledgeRetrievalService).retrieveKnowledge(anyString(), eq(adminContext), eq(4));
        verify(chatModel).call(any(Prompt.class));
        verifyNoInteractions(productLookupTool);
    }

    @Test
    @DisplayName("Zero chunks retrieved triggers deterministic fail-safe response without LLM invocation")
    void shouldReturnDeterministicFailSafeWhenNoDocumentsFound() {
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), any(ErpAiExecutionContext.class), anyInt()))
                .thenReturn(List.of());

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("What is our policy on space shuttle docking?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("I could not find any company policy or documentation covering this in the knowledge base"));

        // ChatModel must NOT be called when no documents are matched (deterministic zero-token fail-safe)
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("Mixed turn conversation correctly routes tool query, knowledge query, and conversational greeting")
    void shouldHandleMixedTurnConversation() {
        // Turn 1: Tool query
        when(productLookupTool.getProductDetails(eq("SKU-100"), any())).thenReturn(
                List.of(Map.of("id", 1, "sku", "SKU-100", "name", "Brembo Brake Pads"))
        );

        String turn1Response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("Show product details for SKU-100")),
                adminContext
        );
        assertNotNull(turn1Response);
        verify(productLookupTool).getProductDetails(eq("SKU-100"), any());

        // Turn 2: Knowledge query
        RetrievedChunk warrantyChunk = new RetrievedChunk(
                2L,
                UUID.randomUUID(),
                "POL-WAR-01",
                "Warranty Policy",
                1,
                "Coverage",
                "Standard warranty covers 12 months from installation.",
                20,
                0.9
        );
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), any(ErpAiExecutionContext.class), anyInt()))
                .thenReturn(List.of(warrantyChunk));

        ChatResponse mockGrounded = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockGrounded.getResult().getOutput().getText()).thenReturn(
                "Standard warranty covers 12 months. [Source: Warranty Policy v1, §Coverage]"
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockGrounded);

        String turn2Response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(
                        new UserMessage("Show product details for SKU-100"),
                        new AssistantMessage(turn1Response),
                        new UserMessage("What is our warranty policy on brake pads?")
                ),
                adminContext
        );
        assertNotNull(turn2Response);
        assertTrue(turn2Response.contains("12 months"));
        assertTrue(turn2Response.contains("[Source: Warranty Policy v1, §Coverage]"));

        // Turn 3: Conversational query
        ChatResponse mockGreeting = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockGreeting.getResult().getOutput().getText()).thenReturn("You are welcome! Let me know if you need anything else.");
        when(chatModel.call(any(Prompt.class))).thenReturn(mockGreeting);

        String turn3Response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(
                        new UserMessage("What is our warranty policy on brake pads?"),
                        new AssistantMessage(turn2Response),
                        new UserMessage("Thank you for your help!")
                ),
                adminContext
        );
        assertNotNull(turn3Response);
        assertTrue(turn3Response.contains("welcome"));
    }

    @Test
    @DisplayName("Hallucinated citations are detected and stripped during citation validation")
    void shouldStripHallucinatedCitationsDuringGraphExecution() {
        RetrievedChunk genuineChunk = new RetrievedChunk(
                3L,
                UUID.randomUUID(),
                "POL-DISC-01",
                "Discount Policy",
                1,
                "Cash Discounts",
                "Cash transactions receive a 2% settlement discount.",
                18,
                0.91
        );

        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), any(ErpAiExecutionContext.class), anyInt()))
                .thenReturn(List.of(genuineChunk));

        // Model hallucinates an unauthorized/non-existent document citation
        ChatResponse mockResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockResponse.getResult().getOutput().getText()).thenReturn(
                "Cash transactions get 2% off. [Source: Discount Policy v1, §Cash Discounts] "
                + "And secret executive bonuses are 50%. [Source: Secret Executive Compensation v5, §Bonuses]"
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("What is our discount policy for cash sales?")),
                adminContext
        );

        assertNotNull(response);
        // The genuine citation is preserved
        assertTrue(response.contains("[Source: Discount Policy v1, §Cash Discounts]"));
        // The hallucinated citation is stripped
        assertFalse(response.contains("Secret Executive Compensation"));
    }

    @Test
    @DisplayName("Authorization failure returns permission denied and completely bypasses LLM invocation")
    void shouldReturnAccessDeniedWithoutLlmCallOnAuthorizationFailure() {
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), any(ErpAiExecutionContext.class), anyInt()))
                .thenThrow(new ToolAuthorizationException("Caller lacks required role to access knowledge documentation"));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("What is the refund policy?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("Access denied: you do not have permission to access knowledge documentation"));

        // ChatModel must NOT be called when authorization fails (zero-token fail-closed)
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("Database retrieval error returns temporary system error message and completely bypasses LLM invocation")
    void shouldReturnSystemErrorWithoutLlmCallOnDatabaseFailure() {
        when(knowledgeRetrievalService.retrieveKnowledge(anyString(), any(ErpAiExecutionContext.class), anyInt()))
                .thenThrow(new KnowledgeRetrievalException("Simulated PostgreSQL connection timeout"));

        String response = agentService.chat(
                conversationId,
                UUID.randomUUID(),
                List.of(new UserMessage("What is the return policy?")),
                adminContext
        );

        assertNotNull(response);
        assertTrue(response.contains("I couldn't retrieve the requested documentation from the knowledge base due to a temporary system error"));

        // ChatModel must NOT be called when database error occurs (zero-token error path)
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("Missing KnowledgeRetrievalService returns temporary system error and completely bypasses LLM invocation")
    void shouldReturnSystemErrorWithoutLlmCallWhenRetrievalServiceIsNull() {
        ErpAiGraphNodes nodesWithNullService = new ErpAiGraphNodes(
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
                null, // knowledgeRetrievalService is intentionally null (configuration failure)
                citationValidator
        );

        ErpAiAgentService testAgentService = new ErpAiAgentService(nodesWithNullService, Duration.ofSeconds(10));
        try {
            String response = testAgentService.chat(
                    conversationId,
                    UUID.randomUUID(),
                    List.of(new UserMessage("What is the return policy?")),
                    adminContext
            );

            assertNotNull(response);
            assertTrue(response.contains("I couldn't retrieve the requested documentation from the knowledge base due to a temporary system error"));

            // ChatModel must NOT be called when retrieval service is unavailable
            verifyNoInteractions(chatModel);
        } finally {
            testAgentService.shutdownTimeoutScheduler();
        }
    }
}
