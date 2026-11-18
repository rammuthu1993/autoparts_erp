package com.autoparts.erp.ai.rag.retrieval;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

/**
 * PostgreSQL native Full-Text Search (FTS) lexical retrieval engine.
 *
 * <p>Uses {@code websearch_to_tsquery('english', :queryText)} for natural query syntax parsing,
 * the GIN index on {@code chunk_vector}, and {@code ts_rank_cd(..., 5)} combining
 * logarithmic document-length normalization (flag 1) with cover-density extent distance normalization (flag 4).</p>
 */
@Component
public class PostgresFtsRetrievalEngine implements KnowledgeRetrievalEngine {

    private static final Logger log = LoggerFactory.getLogger(PostgresFtsRetrievalEngine.class);

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    @Autowired
    public PostgresFtsRetrievalEngine(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    @Override
    public List<RetrievedChunk> retrieve(String queryText, Set<String> allowedRoles, int limit) {
        if (!StringUtils.hasText(queryText) || allowedRoles == null || allowedRoles.isEmpty() || limit <= 0) {
            return List.of();
        }

        String sql = """
                SELECT c.id AS chunk_id,
                       c.document_id,
                       d.document_code,
                       d.title AS document_title,
                       d.version AS document_version,
                       c.section_title,
                       c.chunk_text,
                       c.token_count,
                       ts_rank_cd(c.chunk_vector, websearch_to_tsquery('english', :queryText), 5) AS relevance_score
                FROM knowledge_chunks c
                JOIN knowledge_documents d ON c.document_id = d.id
                WHERE c.active = TRUE
                  AND d.active = TRUE
                  AND d.required_role IN (:allowedRoles)
                  AND c.chunk_vector @@ websearch_to_tsquery('english', :queryText)
                ORDER BY relevance_score DESC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryText", queryText.trim())
                .addValue("allowedRoles", allowedRoles)
                .addValue("limit", limit);

        try {
            List<RetrievedChunk> chunks = namedJdbcTemplate.query(sql, params, (rs, rowNum) -> new RetrievedChunk(
                    rs.getLong("chunk_id"),
                    (UUID) rs.getObject("document_id"),
                    rs.getString("document_code"),
                    rs.getString("document_title"),
                    rs.getInt("document_version"),
                    rs.getString("section_title"),
                    rs.getString("chunk_text"),
                    rs.getInt("token_count"),
                    rs.getDouble("relevance_score")
            ));

            log.info("FTS lexical retrieval completed for query '{}'. Matched chunks: {}", queryText, chunks.size());
            return chunks;
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("PostgreSQL FTS retrieval query failed for query '{}': {}", queryText, e.getMessage(), e);
            throw new KnowledgeRetrievalException("Database query failed during knowledge retrieval: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during knowledge retrieval for query '{}': {}", queryText, e.getMessage(), e);
            throw new KnowledgeRetrievalException("Knowledge retrieval encountered an unexpected failure: " + e.getMessage(), e);
        }
    }

    @Override
    public String getEngineType() {
        return "POSTGRES_FTS_LEXICAL";
    }
}
