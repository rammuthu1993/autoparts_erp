package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.autoparts.erp.ai.tools.PurchaseLookupTool;

@ExtendWith(MockitoExtension.class)
class ErpAiPurchaseLookupTest {

    @Mock private ChatModel chatModel;
    @Mock private ProductLookupTool productLookupTool;
    @Mock private InventoryHistoryLookupTool inventoryHistoryLookupTool;
    @Mock private PurchaseLookupTool purchaseLookupTool;

    private ErpAiGraphNodes nodes;
    private RunnableConfig config;

    @BeforeEach
    void setUp() {
        nodes = new ErpAiGraphNodes(chatModel, productLookupTool,
                new ErpAiIntentClassifier(), inventoryHistoryLookupTool, purchaseLookupTool);
        config = RunnableConfig.builder().build();
    }

    @Test
    void classifiesPurchaseSearchAndRoutesToTool() {
        var classified = nodes.classifyIntent(new ErpAiGraphState(Map.of(
                "currentMessage", "Show recent purchases")), config);

        assertEquals("PURCHASE_SEARCH", classified.get("intent"));
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(
                new ErpAiGraphState(Map.of("intent", "PURCHASE_SEARCH"))));
    }

    @Test
    void classifiesPurchaseDetailsAndRoutesToTool() {
        var classified = nodes.classifyIntent(new ErpAiGraphState(Map.of(
                "currentMessage", "Show purchase details for PUR-00000012")), config);

        assertEquals("PURCHASE_DETAILS", classified.get("intent"));
        assertEquals("PUR-00000012", classified.get("searchText"));
        assertEquals("TOOL", ErpAiGraphNodes.routeIntent(
                new ErpAiGraphState(Map.of("intent", "PURCHASE_DETAILS"))));
    }

    @Test
    void executesRecentPurchaseSearchWithSafeLimit() {
        List<Map<String, Object>> purchases = List.of(Map.of(
                "purchase_number", "PUR-00000012"));
        when(purchaseLookupTool.searchPurchases("", 10)).thenReturn(purchases);

        var result = nodes.executeProductTool(new ErpAiGraphState(Map.of(
                "intent", "PURCHASE_SEARCH",
                "searchText", "",
                "limit", 10)), config);

        assertEquals(purchases, result.get("toolResult"));
        assertEquals("", result.get("toolError"));
        verify(purchaseLookupTool).searchPurchases("", 10);
    }

    @Test
    void formatsPurchaseSearchResultsDeterministically() {
        var state = new ErpAiGraphState(Map.of(
                "intent", "PURCHASE_SEARCH",
                "toolError", "",
                "toolResult", List.of(Map.of(
                        "purchase_number", "PUR-00000012",
                        "supplier_name", "ABC Auto Parts",
                        "supplier_invoice_no", "INV-009",
                        "purchase_date", "2026-11-10",
                        "status", "RECEIVED",
                        "product_count", 3,
                        "grand_total", "12500.00"))));

        var result = nodes.generateResponse(state, config);
        String response = (String) result.get("finalResponse");

        org.junit.jupiter.api.Assertions.assertTrue(response.contains("PUR-00000012"));
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("ABC Auto Parts"));
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("12500.00"));
    }

    @Test
    void formatsPurchaseDetailsAndProductLines() {
        var state = new ErpAiGraphState(Map.of(
                "intent", "PURCHASE_DETAILS",
                "toolError", "",
                "toolResult", List.of(Map.ofEntries(
                        Map.entry("purchase_number", "PUR-00000012"),
                        Map.entry("supplier_name", "ABC Auto Parts"),
                        Map.entry("supplier_invoice_no", "INV-009"),
                        Map.entry("purchase_date", "2026-11-10"),
                        Map.entry("status", "RECEIVED"),
                        Map.entry("subtotal", "10000.00"),
                        Map.entry("discount_total", "500.00"),
                        Map.entry("tax_total", "1800.00"),
                        Map.entry("grand_total", "11300.00"),
                        Map.entry("product_code", "GAP-000001"),
                        Map.entry("part_no", "BP-FRONT-001"),
                        Map.entry("product_name", "Brake Pad Front"),
                        Map.entry("quantity", "20.000"),
                        Map.entry("purchase_price", "500.00"),
                        Map.entry("discount_amount", "0.00"),
                        Map.entry("tax_amount", "1800.00"),
                        Map.entry("line_total", "11300.00")))));

        var result = nodes.generateResponse(state, config);
        String response = (String) result.get("finalResponse");

        org.junit.jupiter.api.Assertions.assertTrue(response.contains("PUR-00000012"));
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("GAP-000001"));
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("Brake Pad Front"));
    }

    @Test
    void returnsClearMessageWhenNoPurchaseMatches() {
        var state = new ErpAiGraphState(Map.of(
                "intent", "PURCHASE_DETAILS",
                "toolError", "",
                "toolResult", List.of()));

        var result = nodes.generateResponse(state, config);
        assertEquals(true, ((String) result.get("finalResponse")).contains("No matching purchase records"));
    }
}
