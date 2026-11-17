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
@DisplayName("ReportLookupTool Unit and Security Guard Tests")
class ReportLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ReportLookupTool reportLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;

    @BeforeEach
    void setUp() {
        reportLookupTool = new ReportLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    @Test
    @DisplayName("Manager is ALLOWED to get KPI summary and calculate estimated net cash inflow")
    void shouldAllowManagerToGetKpiSummary() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("completed_sales_count", 20, "total_revenue", 100000.0, "collected_revenue", 80000.0)))
                .thenReturn(List.of(Map.of("received_purchases_count", 5, "total_procurement_cost", 40000.0)))
                .thenReturn(List.of(Map.of("total_expenses_count", 10, "total_operating_expenses", 15000.0)))
                .thenReturn(List.of(Map.of("sales_returns_count", 2, "total_returned_amount", 2000.0)));

        Map<String, Object> kpi = reportLookupTool.getKpiSummary("2026-01-01", "2026-01-31", managerContext);

        assertNotNull(kpi);
        assertEquals(20, kpi.get("completed_sales_count"));
        assertEquals(100000.0, kpi.get("total_revenue"));
        assertEquals(15000.0, kpi.get("total_operating_expenses"));
        // Net cash inflow: collected_revenue (80000) - operating_expenses (15000) = 65000.0
        assertEquals(65000.0, kpi.get("estimated_net_cash_inflow"));
        verify(jdbcTemplate, times(4)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Cashier is strictly BLOCKED from KPI summary, ABC inventory, and RFM reports with ZERO JDBC")
    void shouldBlockCashierFromReportsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getKpiSummary("2026-01-01", "2026-01-31", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getInventoryAbcXyzAnalysis(cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getCustomerRfmAnalysis(cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getReconciliationDiscrepancies("2026-01-01", "2026-01-31", cashierContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Manager is strictly BLOCKED from @AdminOnly reconciliation discrepancies with ZERO JDBC")
    void shouldBlockManagerFromReconciliationDiscrepanciesWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getReconciliationDiscrepancies("2026-01-01", "2026-01-31", managerContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Admin is ALLOWED to access reconciliation discrepancies across cash sessions and sales invoices")
    void shouldAllowAdminToAccessReconciliationDiscrepancies() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("register_code", "CR-01", "difference_amount", -200.0)))
                .thenReturn(List.of(Map.of("invoice_number", "INV-100", "discrepancy", 50.0)));

        Map<String, Object> result = reportLookupTool.getReconciliationDiscrepancies("2026-01-01", "2026-01-31", adminContext);

        assertNotNull(result);
        assertEquals(1, result.get("cash_variances_count"));
        assertEquals(1, result.get("invoice_mismatches_count"));
        assertTrue(result.containsKey("cash_variances"));
        assertTrue(result.containsKey("invoice_mismatches"));
        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is ALLOWED to get Inventory ABC Analysis")
    void shouldAllowManagerToGetInventoryAbcAnalysis() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("product_code", "BRK-001", "total_sales_revenue", 75000.0)));

        List<Map<String, Object>> result = reportLookupTool.getInventoryAbcXyzAnalysis(managerContext);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BRK-001", result.get(0).get("product_code"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is ALLOWED to get Customer RFM Analysis")
    void shouldAllowManagerToGetCustomerRfmAnalysis() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("customer_code", "CUST-001", "rfm_segment", "VIP / Champion")));

        List<Map<String, Object>> result = reportLookupTool.getCustomerRfmAnalysis(managerContext);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("VIP / Champion", result.get(0).get("rfm_segment"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Cashier has @SalesAccess to explain COGS & gross profit limitation without calling JDBC")
    void shouldAllowCashierToExplainCogsLimitationWithoutJdbc() {
        Map<String, Object> result = reportLookupTool.explainCogsLimitation(cashierContext);

        assertNotNull(result);
        assertEquals("UNSUPPORTED_METRIC", result.get("status"));
        assertTrue(result.containsKey("explanation"));
        assertTrue(result.get("explanation").toString().contains("does not record a historical transaction-level Cost of Goods Sold"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with ZERO JDBC calls")
    void shouldFailClosedWhenContextMissing() {
        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getKpiSummary("2026-01-01", "2026-01-31", null));

        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getInventoryAbcXyzAnalysis(null));

        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.getReconciliationDiscrepancies("2026-01-01", "2026-01-31", null));

        assertThrows(ToolAuthorizationException.class,
                () -> reportLookupTool.explainCogsLimitation(null));

        verifyNoInteractions(jdbcTemplate);
    }
}
