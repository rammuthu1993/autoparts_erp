
package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ChatModel;

import com.autoparts.erp.ai.tools.ProductLookupTool;

@ExtendWith(MockitoExtension.class)
class ErpAiGraphNodesTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ProductLookupTool productLookupTool;

    private ErpAiGraphNodes nodes;

    private RunnableConfig config;

    @BeforeEach
    void setUp() {
        nodes = new ErpAiGraphNodes(
                chatModel,
                productLookupTool
        );

        config = RunnableConfig.builder().build();
    }

    @Test
    void shouldClassifyProductSearch() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "Find product GAP-000002"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "PRODUCT_SEARCH",
                result.get("intent")
        );

        assertEquals(
                "GAP-000002",
                result.get("searchText")
        );
    }

    @Test
    void shouldClassifyProductStock() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "How much stock is available for GAP-000002?"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "PRODUCT_STOCK",
                result.get("intent")
        );
    }

    @Test
    void shouldClassifyProductCount() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "How many products are available?"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "COUNT_PRODUCTS",
                result.get("intent")
        );
    }

    @Test
    void shouldClassifyLowStock() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "Show low stock products"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "LOW_STOCK_PRODUCTS",
                result.get("intent")
        );
    }

    @Test
    void shouldClassifyUnsupportedRequest() {

        var state = new ErpAiGraphState(
                Map.of(
                        "currentMessage",
                        "Show supplier details"
                )
        );

        var result = nodes.classifyIntent(state, config);

        assertEquals(
                "UNSUPPORTED",
                result.get("intent")
        );
    }

    @Test
    void shouldRouteProductSearchToTool() {

        var state = new ErpAiGraphState(
                Map.of("intent", "PRODUCT_SEARCH")
        );

        assertEquals(
                "TOOL",
                ErpAiGraphNodes.routeIntent(state)
        );
    }

    @Test
    void shouldRouteGeneralQuestionToResponse() {

        var state = new ErpAiGraphState(
                Map.of("intent", "GENERAL")
        );

        assertEquals(
                "GENERAL",
                ErpAiGraphNodes.routeIntent(state)
        );
    }

    @Test
    void shouldExecuteProductSearchTool() {

        List<Map<String, Object>> products = List.of(
                Map.of(
                        "product_code", "GAP-000002",
                        "name", "Brake Pad (Rear)",
                        "qty", 31
                )
        );

        when(productLookupTool.searchProducts("GAP-000002"))
                .thenReturn(products);

        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "PRODUCT_SEARCH",
                        "searchText", "GAP-000002"
                )
        );

        var result = nodes.executeProductTool(state, config);

        assertEquals(
                products,
                result.get("toolResult")
        );

        assertEquals(
                "",
                result.get("toolError")
        );

        verify(productLookupTool)
                .searchProducts("GAP-000002");
    }

    @Test
    void shouldReturnToolErrorWhenDatabaseLookupFails() {

        when(productLookupTool.searchProducts("GAP-000002"))
                .thenThrow(
                        new IllegalStateException("Database unavailable")
                );

        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "PRODUCT_SEARCH",
                        "searchText", "GAP-000002"
                )
        );

        var result = nodes.executeProductTool(state, config);

        assertEquals(
                "Unable to retrieve the requested ERP information.",
                result.get("toolError")
        );
    }

    @Test
    void shouldPropagateCancellation() {

        when(productLookupTool.searchProducts("GAP-000002"))
                .thenThrow(
                        new CancellationException("Cancelled")
                );

        var state = new ErpAiGraphState(
                Map.of(
                        "intent", "PRODUCT_SEARCH",
                        "searchText", "GAP-000002"
                )
        );

        assertThrows(
                CancellationException.class,
                () -> nodes.executeProductTool(state, config)
        );
    }
}
