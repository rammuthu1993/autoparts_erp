
package com.autoparts.erp.audit.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * @deprecated Use {@link #findPaged(int, int, String, String, String, String, String)} for standard paginated access.
     */
    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 50, pagedUrl = "/api/v1/admin/audit-logs/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @GetMapping
    @AdminOnly
    public List<AuditLog> getRecentLogs(
            @RequestParam(required = false, defaultValue = "50") String limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        int parsedLimit = parseLimit(limit, 50);
        return auditLogService.getRecentLogs(parsedLimit, offset);
    }

    private int parseLimit(String limitStr, int defaultLimit) {
        if (limitStr == null || limitStr.isBlank()) {
            return defaultLimit;
        }
        try {
            int parsed = Integer.parseInt(limitStr.trim());
            if (parsed <= 0) {
                return defaultLimit;
            }
            return Math.min(parsed, defaultLimit);
        } catch (NumberFormatException e) {
            return defaultLimit;
        }
    }

    @GetMapping("/paged")
    @AdminOnly
    public ResponseEntity<PageResponse<AuditLog>> findPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(
                auditLogService.findPaged(page, size, search, action, entityType, sortBy, sortDir)
        );
    }
}
