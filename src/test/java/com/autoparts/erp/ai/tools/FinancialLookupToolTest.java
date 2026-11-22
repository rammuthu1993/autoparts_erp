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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinancialLookupTool Unit and Security Guard Tests")
class FinancialLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FinancialLookupTool financialLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;

    @BeforeEach
    void setUp() {
        financialLookupTool = new FinancialLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    @Test
    @DisplayName("Cashier has @SalesAccess to get open cash register sessions")
    void shouldAllowCashierToGetOpenCashRegisters() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "register_code", "CR-01",
                        "register_name", "Counter 1",
                        "session_id", 101L,
                        "current_drawer_balance", 12500.0
                )));

        List<Map<String, Object>> result = financialLookupTool.getOpenCashRegisterStatus(cashierContext);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("CR-01", result.get(0).get("register_code"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Cashier is strictly BLOCKED from expense summary, daily closing, and cash discrepancies with ZERO JDBC")
    void shouldBlockCashierFromExpensesAndClosingsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getExpenseSummary("2026-01-01", "2026-01-31", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getExpensesByCategory("Utilities", "2026-01-01", "2026-01-31", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getDailyClosing("2026-01-15", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getCashDiscrepancies("2026-01-01", "2026-01-31", cashierContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Manager is ALLOWED to get expense summary with category breakdown")
    void shouldAllowManagerToGetExpenseSummary() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("total_count", 15, "total_expense_amount", 45000.0, "cash_expense", 12000.0)))
                .thenReturn(List.of(Map.of("category_name", "Rent", "category_total", 30000.0)));

        Map<String, Object> summary = financialLookupTool.getExpenseSummary("2026-01-01", "2026-01-31", managerContext);

        assertNotNull(summary);
        assertEquals(15, summary.get("total_count"));
        assertEquals(45000.0, summary.get("total_expense_amount"));
        assertTrue(summary.containsKey("categories"));
        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is ALLOWED to search expenses by category")
    void shouldAllowManagerToSearchExpensesByCategory() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("title", "Office Rent", "amount", 25000.0)));

        List<Map<String, Object>> result = financialLookupTool.getExpensesByCategory("Rent", "2026-01-01", "2026-01-31", managerContext);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Office Rent", result.get(0).get("title"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is ALLOWED to get daily closing for a date")
    void shouldAllowManagerToGetDailyClosing() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("business_date", LocalDate.parse("2026-01-15"), "sales_total", 85000.0)));

        Map<String, Object> closing = financialLookupTool.getDailyClosing("2026-01-15", managerContext);

        assertNotNull(closing);
        assertEquals(85000.0, closing.get("sales_total"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is ALLOWED to check cash session discrepancies")
    void shouldAllowManagerToGetCashDiscrepancies() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("register_code", "CR-01", "difference_amount", -150.0)));

        List<Map<String, Object>> result = financialLookupTool.getCashDiscrepancies("2026-01-01", "2026-01-31", managerContext);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(-150.0, result.get(0).get("difference_amount"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Empty date string in getDailyClosing returns empty map without JDBC")
    void shouldReturnEmptyForEmptyDailyClosingDateWithoutJdbc() {
        Map<String, Object> result = financialLookupTool.getDailyClosing("", managerContext);
        assertTrue(result.isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Invalid date range in getExpenseSummary throws ClientValidationException without JDBC")
    void shouldThrowOnInvalidDateRangeInExpenseSummary() {
        assertThrows(ClientValidationException.class,
                () -> financialLookupTool.getExpenseSummary("2026-02-01", "2026-01-01", managerContext));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with ZERO JDBC calls")
    void shouldFailClosedWhenContextMissing() {
        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getOpenCashRegisterStatus(null));

        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getExpenseSummary("2026-01-01", "2026-01-31", null));

        assertThrows(ToolAuthorizationException.class,
                () -> financialLookupTool.getDailyClosing("2026-01-15", null));

        verifyNoInteractions(jdbcTemplate);
    }
}
