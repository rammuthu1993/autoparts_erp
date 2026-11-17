package com.autoparts.erp.ai.service;

import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;
import com.autoparts.erp.ai.tools.*;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stage 2D: Returns, Financial, Reports, and Audit Log Intent Classification, Routing, and Execution Tests")
class Stage2DIntentAndRoutingTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private ProductLookupTool productLookupTool;
    @Mock
    private InventoryHistoryLookupTool inventoryHistoryLookupTool;
    @Mock
    private PurchaseLookupTool purchaseLookupTool;
    @Mock
    private SalesLookupTool salesLookupTool;
    @Mock
    private CustomerLookupTool customerLookupTool;
    @Mock
    private SupplierLookupTool supplierLookupTool;
    @Mock
    private ReturnLookupTool returnLookupTool;
    @Mock
    private FinancialLookupTool financialLookupTool;
    @Mock
    private ReportLookupTool reportLookupTool;
    @Mock
    private AuditLogLookupTool auditLogLookupTool;

    private ErpAiIntentClassifier classifier;
    private ErpAiGraphNodes nodes;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;
    private RunnableConfig adminConfig;
    private RunnableConfig managerConfig;
    private RunnableConfig cashierConfig;

    @BeforeEach
    void setUp() {
        classifier = new ErpAiIntentClassifier();
        nodes = new ErpAiGraphNodes(
                chatModel,
                productLookupTool,
                classifier,
                inventoryHistoryLookupTool,
                purchaseLookupTool,
                salesLookupTool,
                customerLookupTool,
                supplierLookupTool,
                returnLookupTool,
                financialLookupTool,
                reportLookupTool,
                auditLogLookupTool
        );

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));

        adminConfig = RunnableConfig.builder().addMetadata("executionContext", adminContext).build();
        managerConfig = RunnableConfig.builder().addMetadata("executionContext", managerContext).build();
        cashierConfig = RunnableConfig.builder().addMetadata("executionContext", cashierContext).build();
    }

    // =========================================================================
    // 1. Intent Classification Tests (17 Stage 2D Intents)
    // =========================================================================

    @Test
    @DisplayName("Classify Returns intents correctly")
    void shouldClassifyReturnIntents() {
        assertEquals("SALES_RETURN_SEARCH", classifier.classify("find sales return for defective brake pad").intent());
        assertEquals("SALES_RETURN_DETAILS", classifier.classify("sales return details for INV-2026-001").intent());
        assertEquals("PURCHASE_RETURN_SEARCH", classifier.classify("search purchase returns PR-0001").intent());
        assertEquals("PURCHASE_RETURN_DETAILS", classifier.classify("purchase return details for PR-0001").intent());
        assertEquals("RETURN_SUMMARY", classifier.classify("returns summary from 2026-01-01 to 2026-01-31").intent());
    }

    @Test
    @DisplayName("Classify Financial intents correctly")
    void shouldClassifyFinancialIntents() {
        assertEquals("OPEN_CASH_REGISTERS", classifier.classify("show open cash registers").intent());
        assertEquals("EXPENSE_SUMMARY", classifier.classify("expense summary from 2026-01-01 to 2026-01-31").intent());
        assertEquals("EXPENSES_BY_CATEGORY", classifier.classify("expenses for utilities category").intent());
        assertEquals("DAILY_CLOSING", classifier.classify("daily closing report for 2026-01-15").intent());
        assertEquals("CASH_DISCREPANCIES", classifier.classify("show cash register discrepancies").intent());
    }

    @Test
    @DisplayName("Classify Reports & Analytics intents correctly")
    void shouldClassifyReportIntents() {
        assertEquals("KPI_SUMMARY", classifier.classify("executive kpi summary for this month").intent());
        assertEquals("INVENTORY_ABC_ANALYSIS", classifier.classify("show inventory abc analysis").intent());
        assertEquals("CUSTOMER_RFM_ANALYSIS", classifier.classify("customer rfm segmentation report").intent());
        assertEquals("RECONCILIATION_DISCREPANCIES", classifier.classify("audit reconciliation discrepancies between ledgers").intent());
        assertEquals("COGS_PROFIT_EXPLANATION", classifier.classify("what is our gross profit margin on oil filters").intent());
    }

    @Test
    @DisplayName("Classify Audit & AI Diagnostic Log intents correctly")
    void shouldClassifyAuditLogIntents() {
        assertEquals("AUDIT_LOG_SEARCH", classifier.classify("show recent system audit logs").intent());
        assertEquals("AI_TOOL_RUNS_LOG", classifier.classify("show recent ai tool runs").intent());
    }

    // =========================================================================
    // 2. Intent Routing Tests (All 17 Intents Route to TOOL)
    // =========================================================================

    @Test
    @DisplayName("All 17 Stage 2D intents route to TOOL")
    void shouldRouteAllStage2DIntentsToTool() {
        List<String> stage2DIntents = List.of(
                "SALES_RETURN_SEARCH", "SALES_RETURN_DETAILS", "PURCHASE_RETURN_SEARCH", "PURCHASE_RETURN_DETAILS", "RETURN_SUMMARY",
                "OPEN_CASH_REGISTERS", "EXPENSE_SUMMARY", "EXPENSES_BY_CATEGORY", "DAILY_CLOSING", "CASH_DISCREPANCIES",
                "KPI_SUMMARY", "INVENTORY_ABC_ANALYSIS", "CUSTOMER_RFM_ANALYSIS", "RECONCILIATION_DISCREPANCIES", "COGS_PROFIT_EXPLANATION",
                "AUDIT_LOG_SEARCH", "AI_TOOL_RUNS_LOG"
        );

        for (String intent : stage2DIntents) {
            ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", intent));
            assertEquals("TOOL", ErpAiGraphNodes.routeIntent(state), "Intent " + intent + " must route to TOOL");
        }
    }

    // =========================================================================
    // 3. Security Role Rejection in executeProductTool
    // =========================================================================

    @Test
    @DisplayName("Cashier blocked from Purchase Returns with Admin or Manager message")
    void shouldBlockCashierFromPurchaseReturns() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", "PURCHASE_RETURN_SEARCH", "searchText", "PR-001"));
        when(returnLookupTool.searchPurchaseReturns(eq("PR-001"), any()))
                .thenThrow(new ToolAuthorizationException("Forbidden"));

        Map<String, Object> result = nodes.executeProductTool(state, cashierConfig);

        assertEquals("", result.get("toolResult"));
        assertTrue(result.get("toolError").toString().contains("Required role: Admin or Manager."));
    }

    @Test
    @DisplayName("Cashier blocked from Expenses with Admin or Manager message")
    void shouldBlockCashierFromExpenses() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", "EXPENSE_SUMMARY"));
        when(financialLookupTool.getExpenseSummary(any(), any(), any()))
                .thenThrow(new ToolAuthorizationException("Forbidden"));

        Map<String, Object> result = nodes.executeProductTool(state, cashierConfig);

        assertTrue(result.get("toolError").toString().contains("Required role: Admin or Manager."));
    }

    @Test
    @DisplayName("Cashier blocked from KPI Summary with Admin or Manager message")
    void shouldBlockCashierFromKpis() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", "KPI_SUMMARY"));
        when(reportLookupTool.getKpiSummary(any(), any(), any()))
                .thenThrow(new ToolAuthorizationException("Forbidden"));

        Map<String, Object> result = nodes.executeProductTool(state, cashierConfig);

        assertTrue(result.get("toolError").toString().contains("Required role: Admin or Manager."));
    }

    @Test
    @DisplayName("Manager blocked from @AdminOnly Reconciliation Discrepancies with Admin only message")
    void shouldBlockManagerFromReconciliationDiscrepancies() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", "RECONCILIATION_DISCREPANCIES"));
        when(reportLookupTool.getReconciliationDiscrepancies(any(), any(), any()))
                .thenThrow(new ToolAuthorizationException("Forbidden"));

        Map<String, Object> result = nodes.executeProductTool(state, managerConfig);

        assertTrue(result.get("toolError").toString().contains("Required role: Admin only."));
    }

    @Test
    @DisplayName("Manager blocked from @AdminOnly Audit Logs with Admin only message")
    void shouldBlockManagerFromAuditLogs() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", "AUDIT_LOG_SEARCH", "limit", 10));
        when(auditLogLookupTool.getRecentAuditLogs(any(), anyInt(), any()))
                .thenThrow(new ToolAuthorizationException("Forbidden"));

        Map<String, Object> result = nodes.executeProductTool(state, managerConfig);

        assertTrue(result.get("toolError").toString().contains("Required role: Admin only."));
    }

    @Test
    @DisplayName("Missing execution context returns ACCESS_DENIED immediately")
    void shouldFailClosedWhenConfigMissingContext() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", "SALES_RETURN_SEARCH", "searchText", "INV-001"));
        RunnableConfig emptyConfig = RunnableConfig.builder().build();

        Map<String, Object> result = nodes.executeProductTool(state, emptyConfig);

        assertEquals("", result.get("toolResult"));
        assertTrue(result.get("toolError").toString().contains("ACCESS_DENIED: No execution context provided"));
        verifyNoInteractions(returnLookupTool);
    }

    // =========================================================================
    // 4. Successful Dispatch & Response Generation
    // =========================================================================

    @Test
    @DisplayName("Open cash register status formats correctly")
    void shouldFormatOpenCashRegisters() {
        Map<String, Object> reg = Map.of(
                "register_name", "Main Counter",
                "register_code", "CR-01",
                "session_id", 1001L,
                "opened_by", "cashier1",
                "opened_at", "2026-01-15T09:00:00",
                "opening_balance", 5000.0,
                "net_cash_movement", 12000.0,
                "current_drawer_balance", 17000.0
        );

        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "OPEN_CASH_REGISTERS",
                "toolResult", List.of(reg),
                "toolError", ""
        ));

        Map<String, Object> resp = nodes.generateResponse(state, cashierConfig);

        assertNotNull(resp.get("finalResponse"));
        String text = resp.get("finalResponse").toString();
        assertTrue(text.contains("Open cash register sessions:"));
        assertTrue(text.contains("Main Counter"));
        assertTrue(text.contains("17000.0"));
    }

    @Test
    @DisplayName("COGS & Profit limitation explanation formats correctly")
    void shouldFormatCogsProfitExplanation() {
        Map<String, Object> explanation = Map.of(
                "metric", "Cost of Goods Sold (COGS) & Gross Profit",
                "status", "UNSUPPORTED_METRIC",
                "explanation", "The ERP does not record historical purchase cost in sales line items."
        );

        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "COGS_PROFIT_EXPLANATION",
                "toolResult", explanation,
                "toolError", ""
        ));

        Map<String, Object> resp = nodes.generateResponse(state, cashierConfig);

        assertNotNull(resp.get("finalResponse"));
        String text = resp.get("finalResponse").toString();
        assertTrue(text.contains("ERP Architectural Policy: Cost of Goods Sold (COGS) & Gross Profit"));
        assertTrue(text.contains("UNSUPPORTED_METRIC"));
    }

    @Test
    @DisplayName("Audit logs format correctly")
    void shouldFormatAuditLogs() {
        Map<String, Object> logItem = Map.of(
                "action", "USER_LOGIN",
                "username", "admin",
                "entity_type", "USER",
                "entity_id", 1L,
                "description", "Successful login from 127.0.0.1",
                "created_at", "2026-01-15T10:00:00"
        );

        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "AUDIT_LOG_SEARCH",
                "toolResult", List.of(logItem),
                "toolError", ""
        ));

        Map<String, Object> resp = nodes.generateResponse(state, adminConfig);

        assertNotNull(resp.get("finalResponse"));
        String text = resp.get("finalResponse").toString();
        assertTrue(text.contains("System audit trail events:"));
        assertTrue(text.contains("USER_LOGIN"));
        assertTrue(text.contains("admin"));
    }
}
