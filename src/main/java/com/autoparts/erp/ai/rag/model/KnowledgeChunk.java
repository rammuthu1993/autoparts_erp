package com.autoparts.erp.ai.rag.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain record representing an indexed passage chunk extracted from a knowledge document.
 */
public record KnowledgeChunk(
        Long id,
        UUID documentId,
        int chunkIndex,
        String sectionTitle,
        String chunkText,
        int tokenCount,
        String chunkHashSha256,
        boolean active,
        Instant createdAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    public KnowledgeChunk {
        sectionTitle = sectionTitle == null ? "" : sectionTitle.trim();
        chunkText = chunkText == null ? "" : chunkText.trim();
    }
}
