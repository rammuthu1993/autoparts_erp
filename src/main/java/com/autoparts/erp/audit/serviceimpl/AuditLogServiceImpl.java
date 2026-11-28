
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

    private static final java.util.List<String> SUPPORTED_ACTIONS = java.util.List.of(
            "CREATE", "UPDATE", "DELETE", "CANCEL", "APPROVE", "REFUND", "ADJUST"
    );

    private static final java.util.Set<String> SENSITIVE_KEY_TOKENS = java.util.Set.of(
            "password", "passwordhash", "secret", "jwtsecret", "token", "accesstoken",
            "refreshtoken", "resettoken", "otp", "apikey", "smtppassword", "dbpassword", "privatekey"
    );

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("AUDIT_LOGS.VIEW")
    public PageResponse<AuditLog> findPaged(
            int page,
            int size,
            String search,
            String action,
            String entityType,
            String username,
            String entityId,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate,
            String sortBy,
            String sortDir
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "fromDate cannot be after toDate"
            );
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        boolean defaultQuery = (username == null || username.isBlank())
                && (entityId == null || entityId.isBlank())
                && fromDate == null
                && toDate == null;
        List<AuditLog> content = defaultQuery
                ? auditLogRepository.findPaged(offset, safeSize, search, action, entityType, sortBy, sortDir)
                : auditLogRepository.findPaged(offset, safeSize, search, action, entityType, username, entityId, fromDate, toDate, sortBy, sortDir);

        long total = defaultQuery
                ? auditLogRepository.countPaged(search, action, entityType)
                : auditLogRepository.countPaged(search, action, entityType, username, entityId, fromDate, toDate);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission("AUDIT_LOGS.VIEW")
    public com.autoparts.erp.audit.dto.AuditMetadataResponse getMetadata() {
        List<String> entityTypes = auditLogRepository.findDistinctEntityTypes();
        return new com.autoparts.erp.audit.dto.AuditMetadataResponse(SUPPORTED_ACTIONS, entityTypes);
    }

    private String toJson(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            Object sanitized = sanitizeValue(values);
            return objectMapper.writeValueAsString(sanitized);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize audit log values",
                    exception
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toString() : "";
                String normalizedKey = key.replaceAll("[_\\-\\s]", "").toLowerCase(java.util.Locale.ROOT);
                if (SENSITIVE_KEY_TOKENS.stream().anyMatch(normalizedKey::contains)) {
                    clean.put(key, "[REDACTED]");
                } else {
                    clean.put(key, sanitizeValue(entry.getValue()));
                }
            }
            return clean;
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }
}
