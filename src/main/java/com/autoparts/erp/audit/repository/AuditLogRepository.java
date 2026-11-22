
package com.autoparts.erp.audit.repository;

import java.util.List;

import com.autoparts.erp.audit.model.AuditLog;

public interface AuditLogRepository {

    void insert(
            Long userId,
            String username,
            String action,
            String entityType,
            String entityId,
            String description,
            String oldValues,
            String newValues
    );

    List<AuditLog> findRecent(int limit, int offset);

    List<AuditLog> findPaged(
        int offset,
        int limit,
        String search,
        String action,
        String entityType,
        String sortBy,
        String sortDir
    );

    long countPaged(
        String search,
        String action,
        String entityType
    );
}
