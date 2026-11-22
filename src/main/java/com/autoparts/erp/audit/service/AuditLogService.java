
package com.autoparts.erp.audit.service;

import java.util.List;
import java.util.Map;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.common.dto.PageResponse;

public interface AuditLogService {

    void log(
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    );

    void logSystem(
            String systemIdentifier,
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    );

    void logWithActor(
            String actorType,
            Long actorUserId,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    );

    List<AuditLog> getRecentLogs(int limit, int offset);

    PageResponse<AuditLog> findPaged(
        int page,
        int size,
        String search,
        String action,
        String entityType,
        String sortBy,
        String sortDir
    );
}
