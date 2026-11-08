
package com.autoparts.erp.audit.serviceimpl;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.repository.AuditLogRepository;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.CurrentUserService;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(
            AuditLogRepository auditLogRepository,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void log(
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    ) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new IllegalStateException(
                    "Cannot create an audit log without an authenticated user"
            );
        }

        Long userId = currentUserService.getCurrentUserId();

        String username = authentication.getName();

        String oldJson = toJson(oldValues);
        String newJson = toJson(newValues);

        auditLogRepository.insert(
                userId,
                username,
                action,
                entityType,
                entityId,
                description,
                oldJson,
                newJson
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs(int limit, int offset) {

        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);

        return auditLogRepository.findRecent(
                safeLimit,
                safeOffset
        );
    }

    private String toJson(Map<String, Object> values) {

        if (values == null || values.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize audit log values",
                    exception
            );
        }
    }
}
