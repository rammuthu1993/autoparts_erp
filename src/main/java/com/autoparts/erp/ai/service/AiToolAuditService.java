package com.autoparts.erp.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.autoparts.erp.ai.repository.AiRepository;
import com.autoparts.erp.ai.exception.AiToolAuditPersistenceException;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

import tools.jackson.databind.ObjectMapper;

/**
 * Structured audit persistence for AI tool executions. Development can use best-effort mode;
 * production can fail closed when an audit row cannot be persisted. Raw ERP result rows and
 * free-form search text are deliberately not persisted.
 */
@Service
public class AiToolAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiToolAuditService.class);

    private final AiRepository repository;
    private final ObjectMapper objectMapper;
    private final AiMetricsService metrics;
    private final boolean failClosed;

    @Autowired
    public AiToolAuditService(
            AiRepository repository,
            ObjectMapper objectMapper,
            AiMetricsService metrics,
            @Value("${app.ai.audit.fail-closed:false}") boolean failClosed) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.failClosed = failClosed;
    }

    /** Backward-compatible constructor for unit tests and direct construction. */
    AiToolAuditService(
            AiRepository repository,
            ObjectMapper objectMapper,
            AiMetricsService metrics) {
        this(repository, objectMapper, metrics, false);
    }

    public void record(
            ErpAiExecutionContext context,
            String toolName,
            String searchText,
            String startDate,
            String endDate,
            Integer requestedLimit,
            Object result,
            String status,
            String safeError,
            long durationMs) {

        if (context == null) {
            metrics.recordAuditFailure();
            if (failClosed) {
                throw new AiToolAuditPersistenceException(
                        "AI tool audit requires an authenticated execution context");
            }
            log.error("AI tool audit skipped because execution context is missing. tool={}", safe(toolName));
            return;
        }

        int rowsReturned = rowCount(result);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("intent", safe(toolName));
        arguments.put("searchTextSha256", sha256(searchText));
        arguments.put("searchTextLength", searchText == null ? 0 : searchText.length());
        arguments.put("startDate", safe(startDate));
        arguments.put("endDate", safe(endDate));
        arguments.put("limit", requestedLimit == null ? 0 : requestedLimit);

        Map<String, Object> resultSummary = Map.of(
                "resultType", result == null ? "null" : result.getClass().getSimpleName(),
                "rowsReturned", rowsReturned);

        try {
            repository.saveToolRun(
                    context.conversationId(),
                    context.userSubject(),
                    safe(toolName),
                    objectMapper.writeValueAsString(arguments),
                    objectMapper.writeValueAsString(resultSummary),
                    status,
                    safeError,
                    Math.max(0, durationMs),
                    rowsReturned);
        } catch (Exception exception) {
            metrics.recordAuditFailure();
            log.error("Unable to persist AI tool audit record. tool={}, conversation={}, failClosed={}",
                    safe(toolName), context.conversationId(), failClosed, exception);
            if (failClosed) {
                throw new AiToolAuditPersistenceException(
                        "Required AI tool audit record could not be persisted", exception);
            }
        }
    }

    private static int rowCount(Object result) {
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        if (result instanceof Map<?, ?> map) {
            Object rows = map.get("rows");
            if (rows instanceof Collection<?> collection) {
                return collection.size();
            }
            return map.isEmpty() ? 0 : 1;
        }
        return result == null ? 0 : 1;
    }

    private static String sha256(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.length() <= 100 ? value : value.substring(0, 100);
    }
}
