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

class ErpAiGraphNodesTest {

    private ProductLookupTool products;
    private PurchaseLookupTool purchases;
    private InventoryHistoryLookupTool inventory;
    private ErpAiTools tools;

    @BeforeEach
    void setUp() {
        products = mock(ProductLookupTool.class);
        purchases = mock(PurchaseLookupTool.class);
        inventory = mock(InventoryHistoryLookupTool.class);
        tools = new ErpAiTools(
                products,
                purchases,
                inventory,
                new AiPerformanceMetrics());
    }

    @Test
    void delegatesProductSearch() {
        List<Map<String, Object>> expected = List.of(Map.of("product_code", "PRD001"));
        when(products.searchProducts("brake pad")).thenReturn(expected);
        assertEquals(expected, tools.searchProducts("brake pad"));
        verify(products).searchProducts("brake pad");
    }

    @Test
    void delegatesProductStockLookup() {
        List<Map<String, Object>> expected = List.of(Map.of("product_code", "PRD001", "qty", 12));
        when(products.getProductStock("PRD001")).thenReturn(expected);
        assertEquals(expected, tools.getProductStock("PRD001"));
        verify(products).getProductStock("PRD001");
    }

    @Test
    void delegatesProductCountAndLowStock() {
        when(products.countProducts()).thenReturn(42L);
        List<Map<String, Object>> lowStock = List.of(Map.of("product_code", "PRD002"));
        when(products.getLowStockProducts()).thenReturn(lowStock);

        assertEquals(42L, tools.countProducts());
        assertEquals(lowStock, tools.getLowStockProducts());
    }

    @Test
    void delegatesInventoryMovementDateRange() {
        List<Map<String, Object>> expected = List.of(Map.of("movement_type", "PURCHASE"));
        when(inventory.getProductMovementsByDateRange("PRD001", "2026-10-01", "2026-10-31"))
                .thenReturn(expected);

        assertEquals(expected, tools.getProductMovementsByDateRange(
                "PRD001", "2026-10-01", "2026-10-31"));
        verify(inventory).getProductMovementsByDateRange("PRD001", "2026-10-01", "2026-10-31");
    }
}
