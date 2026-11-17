package com.autoparts.erp.ai.tools;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SupplierLookupTool Unit and Granular Security Guard Tests")
class SupplierLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SupplierLookupTool supplierLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;
    private ErpAiExecutionContext unauthorizedContext;

    @BeforeEach
    void setUp() {
        supplierLookupTool = new SupplierLookupTool(jdbcTemplate);

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
    // Granular Role Enforcement Tests
    // =========================================================================

    @Test
    @DisplayName("Cashier is permitted for supplier directory and contact details (@SalesAccess)")
    void shouldPermitCashierForSupplierDirectoryAndDetails() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("supplier_name", "Bosch India", "phone", "044-12345678")));

        var searchResult = supplierLookupTool.searchSuppliers("Bosch", cashierContext);
        assertFalse(searchResult.isEmpty());

        var details = supplierLookupTool.getSupplierDetails("Bosch", cashierContext);
        assertFalse(details.isEmpty());

        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Cashier is strictly BLOCKED with zero JDBC calls from supplier purchase history and spend totals")
    void shouldBlockCashierFromSupplierPurchasesAndTotalsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierPurchaseHistory("Bosch", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierPurchaseSummary("Bosch", cashierContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Admin and Manager are permitted for supplier purchase history and spend totals")
    void shouldPermitAdminAndManagerForSupplierCommercials() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("purchase_number", "PUR-00000001", "grand_total", 50000.00)));

        var historyAdmin = supplierLookupTool.getSupplierPurchaseHistory("Bosch", adminContext);
        assertFalse(historyAdmin.isEmpty());

        var summaryManager = supplierLookupTool.getSupplierPurchaseSummary("Bosch", managerContext);
        assertFalse(summaryManager.isEmpty());

        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Unauthorized role is blocked from all supplier tools with zero JDBC calls")
    void shouldBlockUnauthorizedRoleFromAllSupplierToolsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.searchSuppliers("Bosch", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierDetails("Bosch", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierPurchaseHistory("Bosch", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierPurchaseSummary("Bosch", unauthorizedContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with zero JDBC calls")
    void shouldFailClosedWhenContextIsMissing() {
        ErpAiContextHolder.clear();

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.searchSuppliers("Bosch"));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierDetails("Bosch"));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierPurchaseHistory("Bosch"));

        assertThrows(ToolAuthorizationException.class,
                () -> supplierLookupTool.getSupplierPurchaseSummary("Bosch"));

        verifyNoInteractions(jdbcTemplate);
    }

    // =========================================================================
    // Query Execution and Parameter Binding Tests
    // =========================================================================

    @Test
    @DisplayName("Empty or whitespace queries return empty results without querying database")
    void shouldHandleEmptyQueriesGracefully() {
        assertTrue(supplierLookupTool.searchSuppliers("", cashierContext).isEmpty());
        assertTrue(supplierLookupTool.searchSuppliers("   ", cashierContext).isEmpty());
        assertTrue(supplierLookupTool.getSupplierDetails("", cashierContext).isEmpty());
        assertTrue(supplierLookupTool.getSupplierPurchaseHistory("", managerContext).isEmpty());
        assertTrue(supplierLookupTool.getSupplierPurchaseSummary("", adminContext).isEmpty());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("getSupplierPurchaseSummary aggregates procurement spend for supplier")
    void shouldRetrieveSupplierPurchaseSummary() {
        Map<String, Object> summary = Map.of(
                "supplier_name", "Bosch India",
                "phone", "044-12345678",
                "total_purchases", 15L,
                "total_purchased", 350000.00,
                "total_received_amount", 300000.00,
                "received_count", 12L,
                "draft_count", 2L,
                "cancelled_count", 1L
        );

        when(jdbcTemplate.queryForList(contains("total_purchases"), any(Object[].class)))
                .thenReturn(List.of(summary));

        Map<String, Object> result = supplierLookupTool.getSupplierPurchaseSummary("Bosch", managerContext);
        assertNotNull(result);
        assertEquals("Bosch India", result.get("supplier_name"));
        assertEquals(350000.00, result.get("total_purchased"));
        assertEquals(15L, result.get("total_purchases"));
    }

    @Test
    @DisplayName("getSupplierPurchaseSummary enforces deterministic ordering on ambiguous partial-name match")
    void shouldOrderDeterministicallyOnPartialNameMatch() {
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql.contains("ORDER BY s.supplier_name ASC, s.id ASC")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "supplier_name", "Bosch Automotive",
                "total_purchases", 5L,
                "total_purchased", 120000.00,
                "total_received_amount", 100000.00,
                "received_count", 4L,
                "draft_count", 1L,
                "cancelled_count", 0L
        )));

        Map<String, Object> result = supplierLookupTool.getSupplierPurchaseSummary("Bosch", managerContext);
        assertNotNull(result);
        assertEquals("Bosch Automotive", result.get("supplier_name"));
        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("ORDER BY s.supplier_name ASC, s.id ASC")),
                any(Object[].class)
        );
    }
}
