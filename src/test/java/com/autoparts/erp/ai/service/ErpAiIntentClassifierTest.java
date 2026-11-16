package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import com.autoparts.erp.ai.tools.ErpAiTools;

class ErpAiIntentClassifierTest {

    @Test
    void exposesOnlyExplicitReadOnlyErpToolMethods() {
        Set<String> names = Arrays.stream(ErpAiTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(java.util.Objects::nonNull)
                .map(Tool::name)
                .collect(Collectors.toSet());

        assertTrue(names.containsAll(Set.of(
                "searchProducts",
                "getProductDetails",
                "getProductStock",
                "countProducts",
                "getLowStockProducts",
                "compareProducts",
                "searchPurchases",
                "getPurchaseDetails",
                "getProductMovementHistory",
                "getProductMovementSummary",
                "getProductMovementsByDateRange",
                "getRecentInventoryMovements",
                "getInventoryAdjustments"
        )));
        assertEquals(13, names.size());
    }

    @Test
    void optionalPurchaseFiltersAreMarkedOptionalForToolSchema() throws Exception {
        Method method = ErpAiTools.class.getMethod(
                "searchPurchases", String.class, String.class, String.class,
                String.class, String.class, Integer.class);

        var parameters = method.getParameters();
        assertEquals(6, parameters.length);
        for (var parameter : parameters) {
            var annotation = parameter.getAnnotation(
                    org.springframework.ai.tool.annotation.ToolParam.class);
            assertNotNull(annotation);
            assertFalse(annotation.required());
        }
    }
}
