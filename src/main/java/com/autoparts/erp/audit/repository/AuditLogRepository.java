package com.autoparts.erp.audit.repository;

import java.time.LocalDate;
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

    default List<AuditLog> findPaged(
        int offset,
        int limit,
        String search,
        String action,
        String entityType,
        String sortBy,
        String sortDir
    ) {
        return findPaged(offset, limit, search, action, entityType, null, null, null, null, sortBy, sortDir);
    }

    default List<AuditLog> findPaged(
        int offset,
        int limit,
        String search,
        String action,
        String entityType,
        String username,
        LocalDate fromDate,
        LocalDate toDate,
        String sortBy,
        String sortDir
    ) {
        return findPaged(offset, limit, search, action, entityType, username, null, fromDate, toDate, sortBy, sortDir);
    }

    List<AuditLog> findPaged(
        int offset,
        int limit,
        String search,
        String action,
        String entityType,
        String username,
        String entityId,
        LocalDate fromDate,
        LocalDate toDate,
        String sortBy,
        String sortDir
    );

    default long countPaged(
        String search,
        String action,
        String entityType
    ) {
        return countPaged(search, action, entityType, null, null, null, null);
    }

    default long countPaged(
        String search,
        String action,
        String entityType,
        String username,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        return countPaged(search, action, entityType, username, null, fromDate, toDate);
    }

    long countPaged(
        String search,
        String action,
        String entityType,
        String username,
        String entityId,
        LocalDate fromDate,
        LocalDate toDate
    );

    List<String> findDistinctEntityTypes();
}
