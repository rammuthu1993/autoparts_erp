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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stage 2C: Purchase, Sales, Customer, and Supplier Intent Classification, Routing, and Execution Tests")
class Stage2CIntentAndRoutingTest {

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

    private ErpAiIntentClassifier classifier;
    private ErpAiGraphNodes nodes;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext cashierContext;
    private RunnableConfig adminConfig;
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
                supplierLookupTool
        );

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));

        adminConfig = RunnableConfig.builder().addMetadata("executionContext", adminContext).build();
        cashierConfig = RunnableConfig.builder().addMetadata("executionContext", cashierContext).build();
    }

    // =========================================================================
    // 1. Intent Classification Tests
    // =========================================================================

    @Test
    @DisplayName("Classify Purchase queries correctly")
    void shouldClassifyPurchaseIntents() {
        // PURCHASE_SEARCH
        var res1 = classifier.classify("search purchases Bosch");
        assertEquals("PURCHASE_SEARCH", res1.intent());
        assertEquals("Bosch", res1.searchText());

        // PURCHASE_DETAILS
        var res2 = classifier.classify("purchase details for PUR-00000001");
        assertEquals("PURCHASE_DETAILS", res2.intent());
        assertEquals("PUR-00000001", res2.searchText());

        // PURCHASE_DATE_RANGE
        var res3 = classifier.classify("purchases from 2026-01-01 to 2026-01-31");
        assertEquals("PURCHASE_DATE_RANGE", res3.intent());
        assertEquals("2026-01-01", res3.startDate());
        assertEquals("2026-01-31", res3.endDate());

        // PURCHASES_BY_SUPPLIER
        var res4 = classifier.classify("purchases from supplier Bosch");
        assertEquals("PURCHASES_BY_SUPPLIER", res4.intent());
        assertEquals("Bosch", res4.searchText());

        // PURCHASE_SUMMARY
        var res5 = classifier.classify("purchase summary between 2026-01-01 and 2026-01-31");
        assertEquals("PURCHASE_SUMMARY", res5.intent());
        assertEquals("2026-01-01", res5.startDate());
        assertEquals("2026-01-31", res5.endDate());

        // PURCHASE_STATUS
        var res6 = classifier.classify("status of purchase PUR-00000001");
        assertEquals("PURCHASE_STATUS", res6.intent());
        assertEquals("PUR-00000001", res6.searchText());
    }

    @Test
    @DisplayName("Classify Sales queries correctly")
    void shouldClassifySalesIntents() {
        // SALES_SEARCH
        var res1 = classifier.classify("search sales Acme");
        assertEquals("SALES_SEARCH", res1.intent());
        assertEquals("Acme", res1.searchText());

        // SALE_DETAILS
        var res2 = classifier.classify("sale details for INV-2026-0001");
        assertEquals("SALE_DETAILS", res2.intent());
        assertEquals("INV-2026-0001", res2.searchText());

        // SALES_DATE_RANGE
        var res3 = classifier.classify("sales between 2026-01-01 and 2026-01-31");
        assertEquals("SALES_DATE_RANGE", res3.intent());
        assertEquals("2026-01-01", res3.startDate());
        assertEquals("2026-01-31", res3.endDate());

        // SALES_BY_CUSTOMER
        var res4 = classifier.classify("sales for customer Acme Garage");
        assertEquals("SALES_BY_CUSTOMER", res4.intent());
        assertEquals("Acme Garage", res4.searchText());

        // SALES_SUMMARY
        var res5 = classifier.classify("sales summary from 2026-01-01 to 2026-01-31");
        assertEquals("SALES_SUMMARY", res5.intent());
        assertEquals("2026-01-01", res5.startDate());
        assertEquals("2026-01-31", res5.endDate());

        // SALE_STATUS
        var res6 = classifier.classify("status of sale INV-2026-0001");
        assertEquals("SALE_STATUS", res6.intent());
        assertEquals("INV-2026-0001", res6.searchText());
    }

    @Test
    @DisplayName("Classify Customer queries correctly")
    void shouldClassifyCustomerIntents() {
        // CUSTOMER_SEARCH
        var res1 = classifier.classify("search customer Acme");
        assertEquals("CUSTOMER_SEARCH", res1.intent());
        assertEquals("Acme", res1.searchText());

        // CUSTOMER_DETAILS
        var res2 = classifier.classify("customer details for CUS000001");
        assertEquals("CUSTOMER_DETAILS", res2.intent());
        assertEquals("CUS000001", res2.searchText());

        // CUSTOMER_SALES_HISTORY
        var res3 = classifier.classify("sales history for customer CUS000001");
        assertEquals("CUSTOMER_SALES_HISTORY", res3.intent());
        assertEquals("CUS000001", res3.searchText());

        // CUSTOMER_OUTSTANDING
        var res4 = classifier.classify("outstanding balance for customer Acme");
        assertEquals("CUSTOMER_OUTSTANDING", res4.intent());
        assertEquals("Acme", res4.searchText());

        var res5 = classifier.classify("how much does customer CUS000001 owe");
        assertEquals("CUSTOMER_OUTSTANDING", res5.intent());
        assertEquals("CUS000001", res5.searchText());
    }

    @Test
    @DisplayName("Classify Supplier queries correctly")
    void shouldClassifySupplierIntents() {
        // SUPPLIER_SEARCH
        var res1 = classifier.classify("find supplier Bosch");
        assertEquals("SUPPLIER_SEARCH", res1.intent());
        assertEquals("Bosch", res1.searchText());

        // SUPPLIER_DETAILS
        var res2 = classifier.classify("supplier details for Bosch");
        assertEquals("SUPPLIER_DETAILS", res2.intent());
        assertEquals("Bosch", res2.searchText());

        // SUPPLIER_PURCHASE_HISTORY
        var res3 = classifier.classify("purchase history for supplier Bosch");
        assertEquals("SUPPLIER_PURCHASE_HISTORY", res3.intent());
        assertEquals("Bosch", res3.searchText());

        // SUPPLIER_SUMMARY
        var res4 = classifier.classify("spend summary for supplier Bosch");
        assertEquals("SUPPLIER_SUMMARY", res4.intent());
        assertEquals("Bosch", res4.searchText());
    }

    // =========================================================================
    // 2. Graph Routing Tests
    // =========================================================================

    @Test
    @DisplayName("routeIntent routes all Stage 2C intents to TOOL")
    void shouldRouteAllStage2CIntentsToTool() {
        List<String> stage2CIntents = List.of(
                "PURCHASE_SEARCH", "PURCHASE_DETAILS", "PURCHASE_DATE_RANGE",
                "PURCHASES_BY_SUPPLIER", "PURCHASE_SUMMARY", "PURCHASE_STATUS",
                "SALES_SEARCH", "SALE_DETAILS", "SALES_DATE_RANGE",
                "SALES_BY_CUSTOMER", "SALES_SUMMARY", "SALE_STATUS",
                "CUSTOMER_SEARCH", "CUSTOMER_DETAILS", "CUSTOMER_SALES_HISTORY", "CUSTOMER_OUTSTANDING",
                "SUPPLIER_SEARCH", "SUPPLIER_DETAILS", "SUPPLIER_PURCHASE_HISTORY", "SUPPLIER_SUMMARY"
        );

        for (String intent : stage2CIntents) {
            ErpAiGraphState state = new ErpAiGraphState(Map.of("intent", intent));
            assertEquals("TOOL", nodes.routeIntent(state), "Expected TOOL route for intent: " + intent);
        }
    }

    // =========================================================================
    // 3. Execution & Dispatch Tests
    // =========================================================================

    @Test
    @DisplayName("executeProductTool dispatches PURCHASE_SEARCH to PurchaseLookupTool")
    void shouldDispatchPurchaseSearch() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "PURCHASE_SEARCH",
                "searchText", "PUR-00000001"
        ));

        when(purchaseLookupTool.searchPurchases(eq("PUR-00000001"), eq(adminContext)))
                .thenReturn(List.of(Map.of(
                        "purchase_number", "PUR-00000001",
                        "supplier_name", "Bosch India",
                        "purchase_date", "2026-01-15",
                        "status", "RECEIVED",
                        "payment_status", "PAID",
                        "grand_total", 45000.00
                )));

        var toolUpdates = nodes.executeProductTool(state, adminConfig);
        assertEquals("", toolUpdates.get("toolError"));

        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(toolUpdates);
        var respUpdates = nodes.generateResponse(new ErpAiGraphState(merged), adminConfig);

        assertNotNull(respUpdates.get("finalResponse"));
        String response = respUpdates.get("finalResponse").toString();
        assertTrue(response.contains("PUR-00000001"));
        assertTrue(response.contains("Bosch India"));
        verify(purchaseLookupTool).searchPurchases("PUR-00000001", adminContext);
    }

    @Test
    @DisplayName("executeProductTool dispatches SALE_DETAILS to SalesLookupTool")
    void shouldDispatchSaleDetails() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "SALE_DETAILS",
                "searchText", "INV-2026-0001"
        ));

        when(salesLookupTool.getSaleDetails(eq("INV-2026-0001"), eq(adminContext)))
                .thenReturn(Map.of(
                        "invoice_number", "INV-2026-0001",
                        "customer_name", "Acme Motors",
                        "sale_date", "2026-02-10",
                        "status", "COMPLETED",
                        "payment_status", "PAID",
                        "grand_total", 8500.00,
                        "items", List.of(Map.of(
                                "product_code", "BRK-PAD-01",
                                "product_name", "Ceramic Brake Pads",
                                "quantity", 2,
                                "unit_price", 4250.00,
                                "line_total", 8500.00
                        )),
                        "payments", List.of(Map.of(
                                "payment_method", "CARD",
                                "amount", 8500.00
                        ))
                ));

        var toolUpdates = nodes.executeProductTool(state, adminConfig);
        assertEquals("", toolUpdates.get("toolError"));

        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(toolUpdates);
        var respUpdates = nodes.generateResponse(new ErpAiGraphState(merged), adminConfig);

        assertNotNull(respUpdates.get("finalResponse"));
        String response = respUpdates.get("finalResponse").toString();
        assertTrue(response.contains("INV-2026-0001"));
        assertTrue(response.contains("Acme Motors"));
        assertTrue(response.contains("BRK-PAD-01"));
        assertTrue(response.contains("Ceramic Brake Pads"));
        verify(salesLookupTool).getSaleDetails("INV-2026-0001", adminContext);
    }

    @Test
    @DisplayName("executeProductTool dispatches CUSTOMER_OUTSTANDING to CustomerLookupTool")
    void shouldDispatchCustomerOutstanding() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "CUSTOMER_OUTSTANDING",
                "searchText", "CUS000001"
        ));

        when(customerLookupTool.getCustomerOutstandingBalance(eq("CUS000001"), eq(adminContext)))
                .thenReturn(Map.of(
                        "customer_code", "CUS000001",
                        "customer_name", "Speedy Garage",
                        "phone", "9876543210",
                        "credit_limit", 50000.00,
                        "outstanding_balance", 12500.00,
                        "unpaid_invoices_count", 3L,
                        "unpaid_invoices_total_balance", 12500.00
                ));

        var toolUpdates = nodes.executeProductTool(state, adminConfig);
        assertEquals("", toolUpdates.get("toolError"));

        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(toolUpdates);
        var respUpdates = nodes.generateResponse(new ErpAiGraphState(merged), adminConfig);

        assertNotNull(respUpdates.get("finalResponse"));
        String response = respUpdates.get("finalResponse").toString();
        assertTrue(response.contains("CUS000001"));
        assertTrue(response.contains("Speedy Garage"));
        assertTrue(response.contains("12500"));
        verify(customerLookupTool).getCustomerOutstandingBalance("CUS000001", adminContext);
    }

    @Test
    @DisplayName("executeProductTool dispatches SUPPLIER_SUMMARY to SupplierLookupTool")
    void shouldDispatchSupplierSummary() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "SUPPLIER_SUMMARY",
                "searchText", "Bosch"
        ));

        when(supplierLookupTool.getSupplierPurchaseSummary(eq("Bosch"), eq(adminContext)))
                .thenReturn(Map.of(
                        "supplier_name", "Bosch India",
                        "phone", "044-12345678",
                        "total_purchases", 15L,
                        "total_purchased", 350000.00,
                        "total_received_amount", 300000.00,
                        "received_count", 12L,
                        "draft_count", 2L,
                        "cancelled_count", 1L
                ));

        var toolUpdates = nodes.executeProductTool(state, adminConfig);
        assertEquals("", toolUpdates.get("toolError"));

        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(toolUpdates);
        var respUpdates = nodes.generateResponse(new ErpAiGraphState(merged), adminConfig);

        assertNotNull(respUpdates.get("finalResponse"));
        String response = respUpdates.get("finalResponse").toString();
        assertTrue(response.contains("Bosch India"));
        assertTrue(response.contains("350000"));
        verify(supplierLookupTool).getSupplierPurchaseSummary("Bosch", adminContext);
    }

    // =========================================================================
    // 4. Role Enforcement During Graph Node Execution
    // =========================================================================

    @Test
    @DisplayName("Cashier execution context produces ACCESS_DENIED for purchase intent in graph node")
    void shouldFailCashierOnPurchaseToolInGraphNode() {
        ErpAiGraphState state = new ErpAiGraphState(Map.of(
                "intent", "PURCHASE_SEARCH",
                "searchText", "PUR-00000001"
        ));

        // When purchaseLookupTool is called with cashierContext, it throws ToolAuthorizationException
        doThrow(new ToolAuthorizationException("Access denied for purchase tool."))
                .when(purchaseLookupTool).searchPurchases("PUR-00000001", cashierContext);

        var toolUpdates = nodes.executeProductTool(state, cashierConfig);

        assertTrue(toolUpdates.containsKey("toolError"));
        String toolError = toolUpdates.get("toolError").toString();
        assertTrue(toolError.startsWith("ACCESS_DENIED:"));
        assertTrue(toolError.contains("Admin or Manager"));

        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(toolUpdates);
        var respUpdates = nodes.generateResponse(new ErpAiGraphState(merged), cashierConfig);

        String finalResponse = respUpdates.get("finalResponse").toString();
        assertTrue(finalResponse.contains("permission to view this ERP data"));
        assertTrue(finalResponse.contains("Admin or Manager"));
    }
}
