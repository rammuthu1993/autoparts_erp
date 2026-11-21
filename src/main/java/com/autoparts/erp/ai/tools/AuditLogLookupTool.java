package com.autoparts.erp.ai.tools;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;

/**
 * Read-only AI tool for System Audit Trail and AI Diagnostic Logs.
 *
 * <p>Strictly protected under {@code @AdminOnly} authorization:
 * Access to security audit logs and AI diagnostic records is restricted exclusively to ROLE_ADMIN.
 * Managers and Cashiers are strictly blocked before any database execution.</p>
 */
@Component
public class AuditLogLookupTool {

    private static final Logger log = LoggerFactory.getLogger(AuditLogLookupTool.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditLogLookupTool(@Qualifier("aiToolJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // --------------------------------------------------
    // Authorization Enforcement (@AdminOnly)
    // --------------------------------------------------

    private void checkAdminOnlyAccess(ErpAiExecutionContext context) {
        if (context == null) {
            throw new ToolAuthorizationException("Access denied. No authenticated execution context provided for AI audit tool.");
        }
        context.requireAnyRole("ROLE_ADMIN");
    }

    private ErpAiExecutionContext resolveContext() {
        return ErpAiContextHolder.getContext().orElseThrow(() ->
                new ToolAuthorizationException("Access denied. No authenticated execution context available for AI audit tool."));
    }

    // --------------------------------------------------
    // 1. Recent System Audit Logs (@AdminOnly)
    // --------------------------------------------------

    @Tool(description = "Retrieve recent system security and administrative audit log events.")
    public List<Map<String, Object>> getRecentAuditLogs(Integer limit) {
        return getRecentAuditLogs(null, limit, resolveContext());
    }

    public List<Map<String, Object>> getRecentAuditLogs(Integer limit, ErpAiExecutionContext context) {
        return getRecentAuditLogs(null, limit, context);
    }

    public List<Map<String, Object>> getRecentAuditLogs(String query, Integer limit, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOnlyAccess(context);

        int maxRows = (limit != null && limit > 0 && limit <= 50) ? limit : 10;
        if (StringUtils.hasText(query)) {
            String pattern = "%" + query.trim() + "%";
            String sql = """
                    SELECT al.id, al.username, al.action, al.entity_type, al.entity_id,
                           al.description, al.created_at
                    FROM audit_logs al
                    WHERE LOWER(al.username) LIKE LOWER(?)
                       OR LOWER(al.action) LIKE LOWER(?)
                       OR LOWER(al.entity_type) LIKE LOWER(?)
                       OR LOWER(al.description) LIKE LOWER(?)
                    ORDER BY al.created_at DESC, al.id DESC
                    LIMIT ?
                    """;
            return queryForList("getRecentAuditLogsWithQuery", sql, pattern, pattern, pattern, pattern, maxRows);
        }

        String sql = """
                SELECT al.id, al.username, al.action, al.entity_type, al.entity_id,
                       al.description, al.created_at
                FROM audit_logs al
                ORDER BY al.created_at DESC, al.id DESC
                LIMIT ?
                """;

        return queryForList("getRecentAuditLogs", sql, maxRows);
    }

    // --------------------------------------------------
    // 2. Recent AI Tool Execution Runs (@AdminOnly)
    // --------------------------------------------------

    @Tool(description = "Retrieve recent AI tool execution records, performance diagnostics, and error statuses.")
    public List<Map<String, Object>> getRecentAiToolRuns(Integer limit) {
        return getRecentAiToolRuns(null, limit, resolveContext());
    }

    public List<Map<String, Object>> getRecentAiToolRuns(Integer limit, ErpAiExecutionContext context) {
        return getRecentAiToolRuns(null, limit, context);
    }

    public List<Map<String, Object>> getRecentAiToolRuns(String query, Integer limit, ErpAiExecutionContext context) {
        checkCancellation();
        checkAdminOnlyAccess(context);

        int maxRows = (limit != null && limit > 0 && limit <= 50) ? limit : 10;
        if (StringUtils.hasText(query)) {
            String pattern = "%" + query.trim() + "%";
            String sql = """
                    SELECT atr.id, atr.conversation_id, atr.user_subject, atr.tool_name,
                           atr.status, atr.execution_duration_ms, atr.rows_returned,
                           atr.error_message, atr.created_at
                    FROM ai_tool_runs atr
                    WHERE LOWER(atr.tool_name) LIKE LOWER(?)
                       OR LOWER(atr.user_subject) LIKE LOWER(?)
                       OR LOWER(atr.status) LIKE LOWER(?)
                    ORDER BY atr.created_at DESC, atr.id DESC
                    LIMIT ?
                    """;
            return queryForList("getRecentAiToolRunsWithQuery", sql, pattern, pattern, pattern, maxRows);
        }

        String sql = """
                SELECT atr.id, atr.conversation_id, atr.user_subject, atr.tool_name,
                       atr.status, atr.execution_duration_ms, atr.rows_returned,
                           atr.error_message, atr.created_at
                FROM ai_tool_runs atr
                ORDER BY atr.created_at DESC, atr.id DESC
                LIMIT ?
                """;

        return queryForList("getRecentAiToolRuns", sql, maxRows);
    }

    // --------------------------------------------------
    // Internal Query & Helper Methods
    // --------------------------------------------------

    private List<Map<String, Object>> queryForList(String operation, String sql, Object... args) {
        checkCancellation();
        long start = System.nanoTime();
        log.info("AI audit tool execution started: {}", operation);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
            checkCancellation();
            log.info("AI audit tool execution completed: {}, resultCount={}, durationMs={}",
                    operation, rows.size(), (System.nanoTime() - start) / 1_000_000);
            return rows;
        } catch (DataAccessException ex) {
            log.error("AI audit tool execution failed: {}", operation, ex);
            throw new IllegalStateException("Unable to retrieve audit information.", ex);
        }
    }

    private void checkCancellation() {
        if (Thread.currentThread().isInterrupted()) {
            log.warn("AI audit tool execution cancelled");
            throw new CancellationException("AI audit tool execution was cancelled.");
        }
    }
}
