
package com.autoparts.erp.audit.model;

import java.time.Instant;

public record AuditLog(
        Long id,
        String uuid,
        Long userId,
        String username,
        String action,
        String entityType,
        String entityId,
        String description,
        String oldValues,
        String newValues,
        Instant createdAt
) {
}
