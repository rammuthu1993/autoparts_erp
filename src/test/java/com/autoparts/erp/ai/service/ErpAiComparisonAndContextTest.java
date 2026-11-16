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

class ErpAiComparisonAndContextTest {

    private ProductLookupTool products;
    private ErpAiTools tools;

    @BeforeEach
    void setUp() {
        products = mock(ProductLookupTool.class);
        tools = new ErpAiTools(
                products,
                mock(PurchaseLookupTool.class),
                mock(InventoryHistoryLookupTool.class),
                new AiPerformanceMetrics());
    }

    @Test
    void comparisonDelegatesAllRequestedProductIdentifiers() {
        List<String> identifiers = List.of("GAP-000001", "GAP-000002");
        List<Map<String, Object>> expected = List.of(
                Map.of("product_code", "GAP-000001"),
                Map.of("product_code", "GAP-000002"));

        when(products.getProductsByIdentifiers(identifiers)).thenReturn(expected);

        assertEquals(expected, tools.compareProducts(identifiers));
        verify(products).getProductsByIdentifiers(identifiers);
    }

    @Test
    void comparisonSupportsMaximumFiveIdentifiers() {
        List<String> identifiers = List.of("A-001", "A-002", "A-003", "A-004", "A-005");
        when(products.getProductsByIdentifiers(identifiers)).thenReturn(List.of());

        assertTrue(tools.compareProducts(identifiers).isEmpty());
        verify(products).getProductsByIdentifiers(identifiers);
    }
}
