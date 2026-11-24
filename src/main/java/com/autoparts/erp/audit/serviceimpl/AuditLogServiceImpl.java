
package com.autoparts.erp.audit.serviceimpl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.util.PaginationUtils;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

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

        Long userId = null;
        String username;
        String actorType;

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {

            actorType = "USER";
            username = authentication.getName();
            try {
                userId = currentUserService.getCurrentUserId();
            } catch (Exception ex) {
                log.warn("Failed to resolve user ID for authenticated user [{}]: {}", username, ex.getMessage());
                userId = null;
            }
        } else {
            actorType = "SYSTEM";
            username = "SYSTEM";
        }

        logWithActor(
                actorType,
                userId,
                username,
                action,
                entityType,
                entityId,
                description,
                oldValues,
                newValues
        );
    }

    @Override
    @Transactional
    public void logSystem(
            String systemIdentifier,
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    ) {
        String identifier = (systemIdentifier != null && !systemIdentifier.isBlank())
                ? systemIdentifier.trim()
                : "SYSTEM";

        logWithActor(
                "SYSTEM",
                null,
                identifier,
                action,
                entityType,
                entityId,
                description,
                oldValues,
                newValues
        );
    }

    @Override
    @Transactional
    public void logWithActor(
            String actorType,
            Long actorUserId,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            String description,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    ) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Audit action must not be blank");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("Audit entityType must not be blank");
        }

        String safeUsername = (actorUsername != null && !actorUsername.isBlank())
                ? actorUsername.trim()
                : (actorType != null ? actorType : "SYSTEM");

        String oldJson = toJson(oldValues);
        String newJson = toJson(newValues);

        auditLogRepository.insert(
                actorUserId,
                safeUsername,
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
    @RequiresPermission("AUDIT_LOGS.VIEW")
    public List<AuditLog> getRecentLogs(int limit, int offset) {

        // Enforce consistent fixed safety ceiling of 50 for the deprecated endpoint
        int safeLimit = (limit <= 0) ? 50 : Math.min(limit, 50);
        int safeOffset = Math.max(0, offset);

        // Fetch safeLimit + 1 rows for accurate limit-plus-one truncation detection
        return auditLogRepository.findRecent(
                safeLimit + 1,
                safeOffset
        );
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("AUDIT_LOGS.VIEW")
    public PageResponse<AuditLog> findPaged(
            int page,
            int size,
            String search,
            String action,
            String entityType,
            String sortBy,
            String sortDir
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        List<AuditLog> content = auditLogRepository.findPaged(
                offset,
                safeSize,
                search,
                action,
                entityType,
                sortBy,
                sortDir
        );

        long total = auditLogRepository.countPaged(search, action, entityType);
        return PageResponse.of(content, safePage, safeSize, total);
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
