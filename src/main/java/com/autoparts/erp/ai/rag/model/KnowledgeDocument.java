package com.autoparts.erp.ai.rag.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain record representing an authoritative knowledge document stored in the ERP database.
 */
public record KnowledgeDocument(
        UUID id,
        String documentCode,
        String title,
        String documentType,
        String contentFormat,
        int version,
        String requiredRole,
        boolean active,
        String fileName,
        String fileHashSha256,
        long fileSizeBytes,
        Map<String, Object> metadata,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    public KnowledgeDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        requiredRole = requiredRole == null ? "PUBLIC" : requiredRole.trim();
    }
}
