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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalesLookupTool Unit and Security Guard Tests")
class SalesLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SalesLookupTool salesLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;
    private ErpAiExecutionContext unauthorizedContext;

    @BeforeEach
    void setUp() {
        salesLookupTool = new SalesLookupTool(jdbcTemplate);

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
    // Security Guard & Role Enforcement Tests (@SalesAccess)
    // =========================================================================

    @Test
    @DisplayName("Cashier is permitted for all sales tools (@SalesAccess)")
    void shouldPermitCashierOnAllSalesTools() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("invoice_number", "INV-2026-0001")));

        var searchResult = salesLookupTool.searchSales("INV-2026", cashierContext);
        assertFalse(searchResult.isEmpty());

        var byCustomer = salesLookupTool.getSalesByCustomer("John", cashierContext);
        assertFalse(byCustomer.isEmpty());

        var byDate = salesLookupTool.getSalesByDateRange("2026-01-01", "2026-01-31", cashierContext);
        assertFalse(byDate.isEmpty());

        var statusResult = salesLookupTool.getSaleStatus("INV-2026-0001", cashierContext);
        assertFalse(statusResult.isEmpty());

        verify(jdbcTemplate, atLeast(4)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Unauthorized role is blocked with zero JDBC calls")
    void shouldBlockUnauthorizedRoleWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.searchSales("INV-2026-0001", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSaleDetails("INV-2026-0001", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSalesByDateRange("2026-01-01", "2026-01-31", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSalesByCustomer("John", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSalesSummary("2026-01-01", "2026-01-31", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSaleStatus("INV-2026-0001", unauthorizedContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with zero JDBC calls")
    void shouldFailClosedWhenContextIsMissing() {
        ErpAiContextHolder.clear();

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.searchSales("INV-2026-0001"));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSaleDetails("INV-2026-0001"));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSalesByDateRange("2026-01-01", "2026-01-31"));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSalesByCustomer("John"));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSalesSummary("2026-01-01", "2026-01-31"));

        assertThrows(ToolAuthorizationException.class,
                () -> salesLookupTool.getSaleStatus("INV-2026-0001"));

        verifyNoInteractions(jdbcTemplate);
    }

    // =========================================================================
    // Parameter Validation & Business Logic Tests
    // =========================================================================

    @Test
    @DisplayName("Empty or whitespace queries return empty results without querying database")
    void shouldHandleEmptyQueriesGracefully() {
        assertTrue(salesLookupTool.searchSales("", cashierContext).isEmpty());
        assertTrue(salesLookupTool.searchSales("   ", cashierContext).isEmpty());
        assertTrue(salesLookupTool.getSaleDetails("", cashierContext).isEmpty());
        assertTrue(salesLookupTool.getSalesByCustomer("", cashierContext).isEmpty());
        assertTrue(salesLookupTool.getSaleStatus("", cashierContext).isEmpty());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Date range validation rejects invalid dates and inverted ranges")
    void shouldValidateDateRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> salesLookupTool.getSalesByDateRange(null, "2026-01-31", managerContext));

        assertThrows(IllegalArgumentException.class,
                () -> salesLookupTool.getSalesByDateRange("2026-01-01", null, managerContext));

        assertThrows(IllegalArgumentException.class,
                () -> salesLookupTool.getSalesByDateRange("2026-02-01", "2026-01-01", managerContext));

        assertThrows(IllegalArgumentException.class,
                () -> salesLookupTool.getSalesSummary("2026-02-01", "2026-01-01", managerContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("getSaleDetails fetches header, items from sales_products, and payments")
    void shouldRetrieveFullSaleDetailsWithItemsAndPayments() {
        Map<String, Object> header = Map.of(
                "id", 101L,
                "invoice_number", "INV-2026-0001",
                "customer_name", "John Doe",
                "grand_total", 1500.00
        );

        Map<String, Object> item = Map.of(
                "id", 501L,
                "product_code", "OIL-5W30",
                "product_name", "Synthetic Engine Oil",
                "quantity", 2,
                "line_total", 1000.00
        );

        Map<String, Object> payment = Map.of(
                "id", 901L,
                "payment_method", "CASH",
                "amount", 1500.00
        );

        when(jdbcTemplate.queryForList(contains("FROM sales s"), any(Object[].class)))
                .thenReturn(List.of(header));
        when(jdbcTemplate.queryForList(contains("FROM sales_products sp"), any(Object[].class)))
                .thenReturn(List.of(item));
        when(jdbcTemplate.queryForList(contains("FROM sales_payments spay"), any(Object[].class)))
                .thenReturn(List.of(payment));

        Map<String, Object> result = salesLookupTool.getSaleDetails("INV-2026-0001", adminContext);

        assertNotNull(result);
        assertEquals("INV-2026-0001", result.get("invoice_number"));
        assertTrue(result.containsKey("items"));
        assertTrue(result.containsKey("payments"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("OIL-5W30", items.get(0).get("product_code"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> payments = (List<Map<String, Object>>) result.get("payments");
        assertEquals(1, payments.size());
        assertEquals("CASH", payments.get(0).get("payment_method"));
    }

    @Test
    @DisplayName("getSalesSummary returns aggregated metrics with parameter binding")
    void shouldCalculateSalesSummary() {
        Map<String, Object> summary = Map.of(
                "total_count", 45L,
                "total_amount", 125000.50,
                "total_paid", 120000.00,
                "total_balance", 5000.50
        );

        when(jdbcTemplate.queryForList(contains("FROM sales"), any(Object[].class)))
                .thenReturn(List.of(summary));

        Map<String, Object> result = salesLookupTool.getSalesSummary("2026-01-01", "2026-01-31", adminContext);
        assertEquals(45L, result.get("total_count"));
        assertEquals(125000.50, result.get("total_amount"));
    }
}
