
package com.autoparts.erp.audit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.annotation.AdminOnly;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @AdminOnly
    public List<AuditLog> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {

        return auditLogService.getRecentLogs(limit, offset);
    }
}
