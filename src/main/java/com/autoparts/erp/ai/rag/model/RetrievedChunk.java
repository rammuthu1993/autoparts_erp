package com.autoparts.erp.ai.rag.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Immutable representation of a retrieved document passage returned by the retrieval engine.
 */
public record RetrievedChunk(
        long chunkId,
        UUID documentId,
        String documentCode,
        String documentTitle,
        int version,
        String sectionTitle,
        String chunkText,
        int tokenCount,
        double relevanceScore
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    public RetrievedChunk {
        documentCode = documentCode == null ? "" : documentCode.trim();
        documentTitle = documentTitle == null ? "" : documentTitle.trim();
        sectionTitle = sectionTitle == null ? "" : sectionTitle.trim();
        chunkText = chunkText == null ? "" : chunkText.trim();
    }

    /**
     * Standard formatted citation for prompt attribution: [Source: <Title> v<Version>, §<Section>]
     */
    public String formatCitation() {
        if (!sectionTitle.isBlank()) {
            return String.format("[Source: %s v%d, §%s]", documentTitle, version, sectionTitle);
        }
        return String.format("[Source: %s v%d]", documentTitle, version);
    }
}
