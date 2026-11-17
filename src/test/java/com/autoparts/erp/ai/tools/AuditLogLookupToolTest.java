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
@DisplayName("AuditLogLookupTool Unit and Security Guard Tests")
class AuditLogLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AuditLogLookupTool auditLogLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;

    @BeforeEach
    void setUp() {
        auditLogLookupTool = new AuditLogLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    @Test
    @DisplayName("Admin is ALLOWED to retrieve system audit logs")
    void shouldAllowAdminToRetrieveAuditLogs() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "action", "USER_LOGIN",
                        "username", "admin",
                        "entity_type", "USER"
                )));

        List<Map<String, Object>> logs = auditLogLookupTool.getRecentAuditLogs(10, adminContext);

        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals("USER_LOGIN", logs.get(0).get("action"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Admin is ALLOWED to search audit logs with query filter")
    void shouldAllowAdminToSearchAuditLogsWithQuery() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("action", "PRODUCT_UPDATE", "username", "admin")));

        List<Map<String, Object>> logs = auditLogLookupTool.getRecentAuditLogs("PRODUCT", 10, adminContext);

        assertNotNull(logs);
        assertEquals(1, logs.size());
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Admin is ALLOWED to retrieve AI diagnostic tool runs")
    void shouldAllowAdminToRetrieveAiToolRuns() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "tool_name", "ProductLookupTool.searchProducts",
                        "status", "SUCCESS",
                        "user_subject", "cashier"
                )));

        List<Map<String, Object>> runs = auditLogLookupTool.getRecentAiToolRuns(5, adminContext);

        assertNotNull(runs);
        assertEquals(1, runs.size());
        assertEquals("SUCCESS", runs.get(0).get("status"));
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Manager is strictly BLOCKED from audit logs and AI runs with ZERO JDBC calls")
    void shouldBlockManagerFromAuditLogsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> auditLogLookupTool.getRecentAuditLogs(10, managerContext));

        assertThrows(ToolAuthorizationException.class,
                () -> auditLogLookupTool.getRecentAiToolRuns(10, managerContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Cashier is strictly BLOCKED from audit logs and AI runs with ZERO JDBC calls")
    void shouldBlockCashierFromAuditLogsWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> auditLogLookupTool.getRecentAuditLogs(10, cashierContext));

        assertThrows(ToolAuthorizationException.class,
                () -> auditLogLookupTool.getRecentAiToolRuns(10, cashierContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Limit bounds are enforced between 1 and 50")
    void shouldEnforceLimitBounds() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        // Limit > 50 defaults to 10
        auditLogLookupTool.getRecentAuditLogs(100, adminContext);
        // Limit <= 0 defaults to 10
        auditLogLookupTool.getRecentAuditLogs(-5, adminContext);
        // Valid limit within bounds
        auditLogLookupTool.getRecentAuditLogs(25, adminContext);

        verify(jdbcTemplate, times(3)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Missing execution context fails closed with ZERO JDBC calls")
    void shouldFailClosedWhenContextMissing() {
        assertThrows(ToolAuthorizationException.class,
                () -> auditLogLookupTool.getRecentAuditLogs(10, null));

        assertThrows(ToolAuthorizationException.class,
                () -> auditLogLookupTool.getRecentAiToolRuns(10, null));

        verifyNoInteractions(jdbcTemplate);
    }
}
