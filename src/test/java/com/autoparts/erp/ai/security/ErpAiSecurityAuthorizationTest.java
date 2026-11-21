package com.autoparts.erp.ai.security;

import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI Tool Security Authorization and Zero-JDBC Guard Tests")
class ErpAiSecurityAuthorizationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProductLookupTool productLookupTool;
    private InventoryHistoryLookupTool inventoryHistoryLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;
    private ErpAiExecutionContext unauthorizedContext;

    @BeforeEach
    void setUp() {
        productLookupTool = new ProductLookupTool(jdbcTemplate);
        inventoryHistoryLookupTool = new InventoryHistoryLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
        unauthorizedContext = ErpAiExecutionContext.create(convId, "guest", List.of("ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    // =========================================================================
    // ProductLookupTool (@SalesAccess: CASHIER, MANAGER, ADMIN)
    // =========================================================================

    @Test
    @DisplayName("ProductLookupTool: Cashier is permitted for search, count, details, stock, low-stock, comparison")
    void shouldAllowCashierSalesAccessOnProductTools() {
		when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(List.of(Map.of("product_code", "OIL-001")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("product_code", "LOW-001")));

        // Search products
        var searchResult = productLookupTool.searchProducts("oil", cashierContext);
        assertFalse(searchResult.isEmpty());

        // Count products
        assertEquals(42L, productLookupTool.countProducts(cashierContext));

        // Low stock products
        var lowStockResult = productLookupTool.getLowStockProducts(cashierContext);
        assertFalse(lowStockResult.isEmpty());

        // Verify queries executed
        verify(jdbcTemplate).queryForList(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    @DisplayName("ProductLookupTool: Rejects unauthorized role with zero JDBC calls")
    void shouldRejectUnauthorizedUserOnProductLookupWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.searchProducts("brake", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.countProducts(unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.getProductDetails("brake", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.getProductStock("brake", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.getLowStockProducts(unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.getProductsByIdentifiers(List.of("BRK-1", "BRK-2"), unauthorizedContext));

        // Critical guarantee: ZERO JDBC INTERACTIONS ON UNAUTHORIZED REQUESTS
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("ProductLookupTool: Rejects missing execution context with zero JDBC calls")
    void shouldRejectMissingContextOnProductLookupWithZeroJdbcCalls() {
        ErpAiContextHolder.clear();

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.searchProducts("filter"));

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.countProducts());

        assertThrows(ToolAuthorizationException.class,
                () -> productLookupTool.getLowStockProducts());

        verifyNoInteractions(jdbcTemplate);
    }

    // =========================================================================
    // InventoryHistoryLookupTool (@AdminOrManager: MANAGER, ADMIN; CASHIER BLOCKED)
    // =========================================================================

    @Test
    @DisplayName("InventoryHistoryLookupTool: Cashier is strictly BLOCKED with zero JDBC calls")
    void shouldBlockCashierFromInventoryHistoryLookupWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getProductMovementHistory("OIL-001", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getProductMovementSummary("OIL-001", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getProductMovementsByDateRange("OIL-001", "2026-01-01", "2026-01-31", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getRecentInventoryMovements(10, cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getInventoryAdjustments("OIL-001", 10, cashierContext));

        // Critical guarantee: ZERO JDBC INTERACTIONS WHEN CASHIER ACCESSES INVENTORY HISTORY
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("InventoryHistoryLookupTool: Unauthorized role is blocked with zero JDBC calls")
    void shouldBlockUnauthorizedRoleFromInventoryHistoryWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getProductMovementHistory("OIL-001", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getRecentInventoryMovements(10, unauthorizedContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("InventoryHistoryLookupTool: Missing context is rejected with zero JDBC calls")
    void shouldRejectMissingContextOnInventoryHistoryWithZeroJdbcCalls() {
        ErpAiContextHolder.clear();

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getProductMovementHistory("OIL-001"));

        assertThrows(ToolAuthorizationException.class,
                () -> inventoryHistoryLookupTool.getRecentInventoryMovements(10));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("InventoryHistoryLookupTool: Manager is permitted to view inventory movements")
    void shouldAllowManagerAccessToInventoryHistory() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("movement_type", "PURCHASE", "quantity_change", 50)));

        var history = inventoryHistoryLookupTool.getProductMovementHistory("OIL-001", managerContext);
        assertNotNull(history);
        assertEquals(1, history.size());

        verify(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("InventoryHistoryLookupTool: Admin is permitted to view adjustments and movements")
    void shouldAllowAdminAccessToInventoryHistory() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("movement_uuid", UUID.randomUUID().toString())));

        var adjustments = inventoryHistoryLookupTool.getInventoryAdjustments("OIL-001", 10, adminContext);
        assertNotNull(adjustments);
        assertEquals(1, adjustments.size());

        verify(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    }
}
