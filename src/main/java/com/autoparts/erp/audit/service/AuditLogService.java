
package com.autoparts.erp.audit.service;

import java.util.List;
import java.util.Map;

import com.autoparts.erp.audit.model.AuditLog;

public interface AuditLogService {

    void log(
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    );

    List<AuditLog> getRecentLogs(int limit, int offset);
}
