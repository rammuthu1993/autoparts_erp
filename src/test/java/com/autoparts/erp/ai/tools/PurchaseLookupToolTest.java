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
@DisplayName("PurchaseLookupTool Unit and Security Guard Tests")
class PurchaseLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PurchaseLookupTool purchaseLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;
    private ErpAiExecutionContext unauthorizedContext;

    @BeforeEach
    void setUp() {
        purchaseLookupTool = new PurchaseLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
        unauthorizedContext = ErpAiExecutionContext.create(convId, "guest", List.of("ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    @Test
    @DisplayName("Cashier is strictly BLOCKED with zero JDBC calls from all purchase queries")
    void shouldBlockCashierFromAllPurchaseToolsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.searchPurchases("PUR-00000001", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchaseDetails("PUR-00000001", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchasesByDateRange("2026-01-01", "2026-01-31", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchasesBySupplier("Bosch", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchaseSummary("2026-01-01", "2026-01-31", cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchaseStatus("PUR-00000001", cashierContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Unauthorized role is blocked with zero JDBC calls")
    void shouldBlockUnauthorizedRoleFromPurchaseToolsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.searchPurchases("PUR-00000001", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchaseDetails("PUR-00000001", unauthorizedContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with zero JDBC calls")
    void shouldFailClosedWhenContextIsMissing() {
        ErpAiContextHolder.clear();

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.searchPurchases("PUR-00000001"));

        assertThrows(ToolAuthorizationException.class,
                () -> purchaseLookupTool.getPurchaseDetails("PUR-00000001"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Manager is permitted to search purchases and view details")
    void shouldAllowManagerToSearchAndRetrievePurchaseDetails() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 101L, "purchase_number", "PUR-00000001", "supplier_name", "Bosch", "grand_total", 5000.00)));

        var searchResult = purchaseLookupTool.searchPurchases("PUR-00000001", managerContext);
        assertFalse(searchResult.isEmpty());
        assertEquals("PUR-00000001", searchResult.get(0).get("purchase_number"));

        var detailsResult = purchaseLookupTool.getPurchaseDetails("PUR-00000001", managerContext);
        assertFalse(detailsResult.isEmpty());
        assertEquals("PUR-00000001", detailsResult.get("purchase_number"));
        assertTrue(detailsResult.containsKey("items"));

        verify(jdbcTemplate, atLeast(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Admin is permitted to retrieve purchase summary and status")
    void shouldAllowAdminToRetrieveSummaryAndStatus() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("total_count", 5, "total_amount", 25000.00, "received_amount", 20000.00, "purchase_number", "PUR-00000001", "status", "RECEIVED", "grand_total", 5000.00)));

        var summary = purchaseLookupTool.getPurchaseSummary("2026-01-01", "2026-01-31", adminContext);
        assertFalse(summary.isEmpty());

        var status = purchaseLookupTool.getPurchaseStatus("PUR-00000001", adminContext);
        assertFalse(status.isEmpty());
        assertEquals("RECEIVED", status.get("status"));
    }

    @Test
    @DisplayName("Date range validation rejects invalid or reversed dates")
    void shouldRejectInvalidOrReversedDateRange() {
        assertThrows(IllegalArgumentException.class,
                () -> purchaseLookupTool.getPurchasesByDateRange("2026-01-31", "2026-01-01", adminContext));

        assertThrows(IllegalArgumentException.class,
                () -> purchaseLookupTool.getPurchasesByDateRange("", "2026-01-01", adminContext));
    }
}
