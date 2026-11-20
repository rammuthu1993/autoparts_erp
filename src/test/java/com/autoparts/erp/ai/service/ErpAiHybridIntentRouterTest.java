package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.autoparts.erp.ai.enums.ClassificationSource;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpAiHybridIntentRouterTest {

    @Test
    @DisplayName("Hybrid: product plus return-policy question routes purchase ERP tool then RAG")
    void shouldRouteProductReturnQuestionAsHybrid() {
        ErpAiIntentResult result = router.route(
                "Can I return GAP-000001 purchased last week?",
                List.of(), conversationId, userContext
        );

        assertEquals("PURCHASE_SEARCH", result.intent());
        assertEquals("GAP-000001", result.searchText());
        assertTrue(result.isMultiIntent());
        assertEquals("KNOWLEDGE", result.secondaryIntent());
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Hybrid: product warranty plus purchase date routes purchase ERP tool then RAG")
    void shouldRouteProductWarrantyAndPurchaseDateQuestionAsHybrid() {
        ErpAiIntentResult result = router.route(
                "What is the warranty for GAP-000001 and when did I purchase it?",
                List.of(), conversationId, userContext
        );

        assertEquals("PURCHASE_SEARCH", result.intent());
        assertEquals("GAP-000001", result.searchText());
        assertTrue(result.isMultiIntent());
        assertEquals("KNOWLEDGE", result.secondaryIntent());
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Hybrid: cash balance plus closing procedure routes cash register tool then RAG")
    void shouldRouteCashBalanceAndClosingProcedureAsHybrid() {
        ErpAiIntentResult result = router.route(
                "Show today's cash balance and explain the closing procedure.",
                List.of(), conversationId, userContext
        );

        assertEquals("OPEN_CASH_REGISTERS", result.intent());
        assertTrue(result.isMultiIntent());
        assertEquals("KNOWLEDGE", result.secondaryIntent());
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    private ErpAiIntentClassifier deterministicClassifier;
    private ErpAiPendingClarificationStore clarificationStore;
    private ErpAiLlmClassifier llmClassifier;
    private ErpAiHybridIntentRouter router;

    private final UUID conversationId = UUID.randomUUID();
    private final ErpAiExecutionContext userContext =
            ErpAiExecutionContext.create(conversationId, "user-alice", Set.of("ROLE_ADMIN", "ROLE_MANAGER"));

    @BeforeEach
    void setUp() {
        deterministicClassifier = new ErpAiIntentClassifier();
        clarificationStore = new ErpAiPendingClarificationStore();
        llmClassifier = mock(ErpAiLlmClassifier.class);
        router = new ErpAiHybridIntentRouter(deterministicClassifier, clarificationStore, llmClassifier);
    }

    @Test
    @DisplayName("Gate 0: 'Show stock of OIL-001 and delete product GAP-000001' must reject entire query as UNSUPPORTED")
    void shouldRejectStockAndDeleteCompoundQuery() {
        ErpAiIntentResult result = router.route(
                "Show stock of OIL-001 and delete product GAP-000001",
                List.of(), conversationId, userContext
        );

        assertEquals("UNSUPPORTED", result.intent());
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Gate 0: 'Show stock of OIL-001 and create purchase' must reject entire query as UNSUPPORTED")
    void shouldRejectStockAndCreatePurchaseCompoundQuery() {
        ErpAiIntentResult result = router.route(
                "Show stock of OIL-001 and create purchase",
                List.of(), conversationId, userContext
        );

        assertEquals("UNSUPPORTED", result.intent());
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Gate 2: 'Show purchases and sales for Acme' must ask for clarification on primary domain")
    void shouldClarifyConflictingPurchasesAndSalesQuery() {
        ErpAiIntentResult result = router.route(
                "Show purchases and sales for Acme",
                List.of(), conversationId, userContext
        );

        assertEquals("CLARIFICATION", result.intent());
        assertTrue(result.clarificationMessage().toLowerCase().contains("purchases")
                && result.clarificationMessage().toLowerCase().contains("sales"));
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Gate 2: 'Show invoice for Acme' must ask for clarification between sales and supplier invoice")
    void shouldClarifyAmbiguousInvoiceQuery() {
        ErpAiIntentResult result = router.route(
                "Show invoice for Acme",
                List.of(), conversationId, userContext
        );

        assertEquals("CLARIFICATION", result.intent());
        assertTrue(result.clarificationMessage().toLowerCase().contains("invoice"));
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Gate 1: Multi-turn clarification resolution across separate calls")
    void shouldResolvePendingClarificationAcrossRequests() {
        // Step 1: User asks for daily closing without date
        ErpAiIntentResult step1 = router.route("daily closing report", List.of(), conversationId, userContext);
        assertEquals("CLARIFICATION", step1.intent());
        assertTrue(step1.clarificationMessage().contains("YYYY-MM-DD"));

        // Verify slot was saved in store
        assertTrue(clarificationStore.findAndValidate(conversationId, "user-alice").isPresent());

        // Step 2: Follow-up request provides date
        ErpAiIntentResult step2 = router.route("2026-11-17", List.of(), conversationId, userContext);

        assertEquals("DAILY_CLOSING", step2.intent());
        assertEquals("2026-11-17", step2.startDate());
        assertEquals(ClassificationSource.CONTEXT_RESOLVED, step2.source());

        // Verify store was cleared
        assertTrue(clarificationStore.findAndValidate(conversationId, "user-alice").isEmpty());
    }

    @Test
    @DisplayName("Gate 1 Security: User B cannot resolve or hijack User A's pending clarification")
    void shouldPreventCrossUserClarificationResolution() {
        // User A registers pending daily closing
        clarificationStore.register(conversationId, "user-alice", "DAILY_CLOSING", "startDate", Map.of());

        // User B attempts to provide date on the same conversationId
        ErpAiExecutionContext attackerContext =
                ErpAiExecutionContext.create(conversationId, "user-bob", Set.of("ROLE_MANAGER"));

        ErpAiIntentResult result = router.route("2026-11-17", List.of(), conversationId, attackerContext);

        // Attacker's follow-up must NOT resolve User A's pending DAILY_CLOSING
        assertFalse("DAILY_CLOSING".equals(result.intent()) && result.source() == ClassificationSource.CONTEXT_RESOLVED);
    }

    @Test
    @DisplayName("Gate 3: Deterministic exact queries must bypass LLM completely")
    void shouldBypassLlmForDeterministicExactMatch() {
        ErpAiIntentResult result = router.route("stock of GAP-000001", List.of(), conversationId, userContext);

        assertEquals("PRODUCT_STOCK", result.intent());
        assertEquals(ClassificationSource.DETERMINISTIC, result.source());
        verify(llmClassifier, never()).classify(anyString(), any());
    }

    @Test
    @DisplayName("Gate 4: Colloquial query with uncertain regex invokes LLM fallback")
    void shouldInvokeLlmForColloquialQuery() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.of(
                new ErpAiIntentResult("PURCHASES_BY_SUPPLIER", "Bosch", "", "", 10, "", List.of(),
                        0.95, ClassificationSource.LLM_FALLBACK, false, "", "")
        ));

        ErpAiIntentResult result = router.route(
                "Can you check what products we bought from Bosch recently?",
                List.of(), conversationId, userContext
        );

        assertEquals("PURCHASES_BY_SUPPLIER", result.intent());
        assertEquals(ClassificationSource.LLM_FALLBACK, result.source());
        assertEquals("Bosch", result.searchText());
    }

    @Test
    @DisplayName("Gate 5: LLM proposing GENERAL for ERP domain query must be overridden to CLARIFICATION")
    void shouldOverrideLlmGeneralToClarificationForDomainQuery() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.of(
                new ErpAiIntentResult("GENERAL", "inventory", "", "", 10, "", List.of(),
                        0.90, ClassificationSource.LLM_FALLBACK, false, "", "")
        ));

        ErpAiIntentResult result = router.route(
                "tell me about recent inventory logs",
                List.of(), conversationId, userContext
        );

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    @DisplayName("Gate 5: LLM failure on query with ERP domain keywords must fail closed to CLARIFICATION")
    void shouldFailClosedToClarificationOnDomainFailure() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.empty());

        ErpAiIntentResult result = router.route(
                "Find the procurement total for last quarter",
                List.of(), conversationId, userContext
        );

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    @DisplayName("Gate 5: Conversational non-ERP greeting routes safely to GENERAL")
    void shouldRouteGreetingToGeneral() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.empty());

        ErpAiIntentResult result = router.route(
                "Hello, good morning!",
                List.of(), conversationId, userContext
        );

        assertEquals("GENERAL", result.intent());
    }

    @Test
    @DisplayName("Gate 5: Advisory confidence below 0.70 overrides to CLARIFICATION")
    void shouldOverrideLowConfidenceToClarification() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.of(
                new ErpAiIntentResult("SALES_SEARCH", "oil", "", "", 10, "", List.of(),
                        0.55, ClassificationSource.LLM_FALLBACK, false, "", "")
        ));

        ErpAiIntentResult result = router.route(
                "maybe some oil thing",
                List.of(), conversationId, userContext
        );

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    @DisplayName("Educational query routes to GENERAL when classified as general")
    void shouldRouteEducationalQuestionsToGeneral() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.empty());

        ErpAiIntentResult res1 = router.route(
                "Explain how product categories work.",
                List.of(), conversationId, userContext
        );
        assertEquals("GENERAL", res1.intent());

        ErpAiIntentResult res2 = router.route(
                "What is an ERP system?",
                List.of(), conversationId, userContext
        );
        assertEquals("GENERAL", res2.intent());
    }

    @Test
    @DisplayName("Live data queries with question prefixes must NEVER reach GENERAL")
    void shouldNeverRouteLiveDataQueriesToGeneral() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.empty());

        // Question with entity reference and live performance cue: routes to domain tool intent (SALES_SEARCH), never GENERAL
        ErpAiIntentResult res1 = router.route(
                "Explain the sales performance of Bosch.",
                List.of(), conversationId, userContext
        );
        assertNotEquals("GENERAL", res1.intent());
        assertEquals("SALES_SEARCH", res1.intent());

        // Question with temporal qualifier and live metric cue: fails closed to CLARIFICATION, never GENERAL
        ErpAiIntentResult res2 = router.route(
                "How does inventory turnover look this month?",
                List.of(), conversationId, userContext
        );
        assertNotEquals("GENERAL", res2.intent());
        assertEquals("CLARIFICATION", res2.intent());

        // Question with specific product code: routes to PRODUCT_STOCK, never GENERAL
        ErpAiIntentResult res3 = router.route(
                "What is the current stock of GAP-000001?",
                List.of(), conversationId, userContext
        );
        assertNotEquals("GENERAL", res3.intent());
        assertEquals("PRODUCT_STOCK", res3.intent());
    }

    @Test
    @DisplayName("Gate 5: LLM proposing GENERAL on live data question with prefix is overridden to CLARIFICATION")
    void shouldOverrideLlmGeneralToClarificationForPrefixLiveDataQuery() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.of(
                new ErpAiIntentResult("GENERAL", "inventory", "", "", 10, "", List.of(),
                        0.90, ClassificationSource.LLM_FALLBACK, false, "", "")
        ));

        // When LLM suggests GENERAL for an inventory turnover query, Gate 5 overrides to CLARIFICATION
        ErpAiIntentResult result = router.route(
                "How does inventory turnover look this month?",
                List.of(), conversationId, userContext
        );

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    @DisplayName("Negative Tests: 'Explain what is the stock?' and 'Explain what is our inventory balance?' must NOT be GENERAL")
    void shouldNeverRouteExplainWhatQueriesToGeneral() {
        when(llmClassifier.classify(anyString(), any())).thenReturn(Optional.empty());

        // 'Explain what is the stock?' asks for live stock without product code -> must clarify, never GENERAL
        ErpAiIntentResult res1 = router.route(
                "Explain what is the stock?",
                List.of(), conversationId, userContext
        );
        assertNotEquals("GENERAL", res1.intent());
        assertEquals("CLARIFICATION", res1.intent());
        assertTrue(res1.clarificationMessage().toLowerCase().contains("product code")
                || res1.clarificationMessage().toLowerCase().contains("part number")
                || res1.clarificationMessage().toLowerCase().contains("records"));

        // 'Explain what is our inventory balance?' asks for live accounting balance -> must fail closed to CLARIFICATION, never GENERAL
        ErpAiIntentResult res2 = router.route(
                "Explain what is our inventory balance?",
                List.of(), conversationId, userContext
        );
        assertNotEquals("GENERAL", res2.intent());
        assertEquals("CLARIFICATION", res2.intent());
    }
}
