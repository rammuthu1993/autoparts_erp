package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autoparts.erp.ai.tools.ErpAiTools;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.autoparts.erp.ai.tools.PurchaseLookupTool;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;

class ErpAiPurchaseLookupTest {

    private PurchaseLookupTool purchaseLookup;
    private ErpAiTools tools;

    @BeforeEach
    void setUp() {
        purchaseLookup = mock(PurchaseLookupTool.class);
        tools = new ErpAiTools(
                mock(ProductLookupTool.class),
                purchaseLookup,
                mock(InventoryHistoryLookupTool.class));
    }

    @Test
    void searchesRecentPurchasesWithDefaultLimitWhenLimitIsNull() {
        List<Map<String, Object>> expected =
                List.of(Map.of("purchase_number", "PUR-00000012"));
        when(purchaseLookup.searchPurchases(null, null, null, null, null, 10))
                .thenReturn(expected);

        assertEquals(expected, tools.searchPurchases(null, null, null, null, null, null));
        verify(purchaseLookup).searchPurchases(null, null, null, null, null, 10);
    }

    @Test
    void passesStatusAndInclusiveDateFiltersToPurchaseLookup() {
        List<Map<String, Object>> expected =
                List.of(Map.of("purchase_number", "PUR-00000012", "status", "RECEIVED"));
        when(purchaseLookup.searchPurchases(
                null, null, "RECEIVED", "2026-10-01", "2026-10-31", 20))
                .thenReturn(expected);

        assertEquals(expected, tools.searchPurchases(
                null, null, "RECEIVED", "2026-10-01", "2026-10-31", 20));
        verify(purchaseLookup).searchPurchases(
                null, null, "RECEIVED", "2026-10-01", "2026-10-31", 20);
    }

    @Test
    void searchesPurchaseBySupplierName() {
        List<Map<String, Object>> expected =
                List.of(Map.of("purchase_number", "PUR-00000012"));
        when(purchaseLookup.searchPurchases(
                "ABC Auto Parts", null, null, null, null, 10))
                .thenReturn(expected);

        assertEquals(expected, tools.searchPurchases(
                "ABC Auto Parts", null, null, null, null, null));
    }

    @Test
    void retrievesPurchaseDetailsByIdentifier() {
        List<Map<String, Object>> expected =
                List.of(Map.of("purchase_number", "PUR-00000012"));
        when(purchaseLookup.getPurchaseDetails("INV-009")).thenReturn(expected);

        assertEquals(expected, tools.getPurchaseDetails("INV-009"));
        verify(purchaseLookup).getPurchaseDetails("INV-009");
    }
}
