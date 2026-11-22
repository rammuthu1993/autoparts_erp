package com.autoparts.erp.ai.tools;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;
import com.autoparts.erp.common.exception.ClientValidationException;
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
@DisplayName("ReturnLookupTool Unit and Security Guard Tests")
class ReturnLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ReturnLookupTool returnLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;

    @BeforeEach
    void setUp() {
        returnLookupTool = new ReturnLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    @Test
    @DisplayName("Cashier has @SalesAccess to search sales returns")
    void shouldAllowCashierToSearchSalesReturns() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 1L, "invoice_number", "INV-001")));

        List<Map<String, Object>> result = returnLookupTool.searchSalesReturns("INV-001", cashierContext);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("INV-001", result.get(0).get("invoice_number"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Cashier has @SalesAccess to get sales return details with line items and refunds")
    void shouldAllowCashierToGetSalesReturnDetails() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 10L, "invoice_number", "INV-002", "total_return_amount", 500.0)))
                .thenReturn(List.of(Map.of("product_name", "Brake Pad", "quantity", 2)))
                .thenReturn(List.of(Map.of("payment_method", "CASH", "amount", 500.0)));

        Map<String, Object> result = returnLookupTool.getSalesReturnDetails("INV-002", cashierContext);

        assertNotNull(result);
        assertEquals("INV-002", result.get("invoice_number"));
        assertTrue(result.containsKey("items"));
        assertTrue(result.containsKey("refunds"));
        verify(jdbcTemplate, times(3)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Empty or null query returns empty without executing JDBC")
    void shouldReturnEmptyForEmptySalesReturnQueryWithoutJdbc() {
        List<Map<String, Object>> result1 = returnLookupTool.searchSalesReturns("", cashierContext);
        List<Map<String, Object>> result2 = returnLookupTool.searchSalesReturns(null, cashierContext);
        Map<String, Object> details = returnLookupTool.getSalesReturnDetails("", cashierContext);

        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
        assertTrue(details.isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Cashier is strictly BLOCKED from supplier purchase returns with ZERO JDBC calls")
    void shouldBlockCashierFromPurchaseReturnsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> returnLookupTool.searchPurchaseReturns("PR-001", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> returnLookupTool.getPurchaseReturnDetails("PR-001", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> returnLookupTool.getReturnSummary("2026-01-01", "2026-01-31", cashierContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Manager is ALLOWED to search purchase returns and get details")
    void shouldAllowManagerToAccessPurchaseReturns() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("return_number", "PR-001", "supplier_name", "Bosch")))
                .thenReturn(List.of(Map.of("id", 5L, "return_number", "PR-001")))
                .thenReturn(List.of(Map.of("product_name", "Spark Plug", "quantity", 10)))
                .thenReturn(List.of(Map.of("credit_note_number", "CN-001", "amount", 1200.0)));

        List<Map<String, Object>> search = returnLookupTool.searchPurchaseReturns("PR-001", managerContext);
        Map<String, Object> details = returnLookupTool.getPurchaseReturnDetails("PR-001", managerContext);

        assertNotNull(search);
        assertEquals(1, search.size());
        assertNotNull(details);
        assertEquals("PR-001", details.get("return_number"));
        assertTrue(details.containsKey("items"));
        assertTrue(details.containsKey("credits"));
        verify(jdbcTemplate, times(4)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is ALLOWED to get consolidated return summary")
    void shouldAllowManagerToGetReturnSummary() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("sales_return_count", 5, "sales_return_amount", 2500.0)))
                .thenReturn(List.of(Map.of("purchase_return_count", 2, "purchase_return_amount", 8000.0)));

        Map<String, Object> summary = returnLookupTool.getReturnSummary("2026-01-01", "2026-01-31", managerContext);

        assertNotNull(summary);
        assertEquals(5, summary.get("sales_return_count"));
        assertEquals(2, summary.get("purchase_return_count"));
        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Invalid date range in return summary throws ClientValidationException without JDBC")
    void shouldThrowOnInvalidDateRangeInReturnSummary() {
        assertThrows(ClientValidationException.class,
                () -> returnLookupTool.getReturnSummary("2026-02-01", "2026-01-01", managerContext));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with ZERO JDBC calls")
    void shouldFailClosedWhenContextMissing() {
        assertThrows(ToolAuthorizationException.class,
                () -> returnLookupTool.searchSalesReturns("INV-001", null));

        assertThrows(ToolAuthorizationException.class,
                () -> returnLookupTool.searchPurchaseReturns("PR-001", null));

        assertThrows(ToolAuthorizationException.class,
                () -> returnLookupTool.getReturnSummary("2026-01-01", "2026-01-31", null));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("ThreadLocal fallback works when explicit context is not supplied")
    void shouldFallbackToThreadLocalContext() {
        ErpAiContextHolder.setContext(cashierContext);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 1L)));

        List<Map<String, Object>> result = returnLookupTool.searchSalesReturns("INV-001");
        assertFalse(result.isEmpty());
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }
}
