package com.autoparts.erp.ai.rag.retrieval;

import java.util.List;
import java.util.Set;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

/**
 * Pluggable retrieval engine interface for the ERP knowledge base.
 */
public interface KnowledgeRetrievalEngine {

    /**
     * Executes knowledge retrieval against the underlying search index.
     *
     * @param queryText natural language search query
     * @param allowedRoles set of authorized roles derived from execution context
     * @param limit maximum number of chunks to return
     * @return ranked list of retrieved chunks
     */
    List<RetrievedChunk> retrieve(String queryText, Set<String> allowedRoles, int limit);

    /**
     * Returns the retrieval engine strategy type.
     */
    String getEngineType();
}
