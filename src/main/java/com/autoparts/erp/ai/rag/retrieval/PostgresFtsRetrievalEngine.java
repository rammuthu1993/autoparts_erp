package com.autoparts.erp.ai.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * PostgreSQL native FTS retrieval with conservative multi-tier fallback.
 *
 * <p>Positive queries use phrase, AND, then weighted-OR retrieval. Constrained
 * natural-language queries never use an unrestricted phrase or OR fallback:
 * their positive and negated content terms are searched together as ordinary
 * lexical anchors, while the original question remains available to the answer
 * generation layer. Natural-language negation is never translated to PostgreSQL
 * {@code -term} exclusion syntax.</p>
 *
 * <p>Every SQL tier applies the same active-document, active-chunk, and immutable
 * role pre-filter. Results are merged by stable chunk ID and ranked by tier,
 * FTS score, chunk index, and chunk ID.</p>
 */
@Component
public class PostgresFtsRetrievalEngine implements KnowledgeRetrievalEngine {

    private static final Logger log = LoggerFactory.getLogger(PostgresFtsRetrievalEngine.class);
    private static final double OR_ANCHOR_WEIGHT = 0.25d;
    private static final int DEFAULT_ANCHOR_TERM_COUNT = 2;

    private static final String SELECT_FROM = """
            SELECT c.id AS chunk_id,
                   c.document_id,
                   d.document_code,
                   d.title AS document_title,
                   d.version AS document_version,
                   c.chunk_index,
                   c.section_title,
                   c.chunk_text,
                   c.token_count,
                   %s AS relevance_score
            FROM knowledge_chunks c
            JOIN knowledge_documents d ON c.document_id = d.id
            CROSS JOIN search_query q
            WHERE c.active = TRUE
              AND d.active = TRUE
              AND d.required_role IN (:allowedRoles)
              AND c.chunk_vector @@ q.tsq
            %s
            ORDER BY relevance_score DESC, c.chunk_index ASC, c.id ASC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final KnowledgeQueryPreprocessor queryPreprocessor;

    @Autowired
    public PostgresFtsRetrievalEngine(
            NamedParameterJdbcTemplate namedJdbcTemplate,
            KnowledgeQueryPreprocessor queryPreprocessor
    ) {
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.queryPreprocessor = queryPreprocessor;
    }

    /** Compatibility constructor for focused tests and manually-created engines. */
    public PostgresFtsRetrievalEngine(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this(namedJdbcTemplate, new KnowledgeQueryPreprocessor());
    }

    @Override
    public List<RetrievedChunk> retrieve(String queryText, Set<String> allowedRoles, int limit) {
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }
        return retrieve(queryPreprocessor.preprocess(queryText), allowedRoles, limit);
    }

    /** Executes the full multi-tier retrieval plan for an already-preprocessed query. */
    public List<RetrievedChunk> retrieve(
            KnowledgeQueryPreprocessor.PreprocessedQuery query,
            Set<String> allowedRoles,
            int limit
    ) {
        if (query == null || query.isEmpty() || allowedRoles == null || allowedRoles.isEmpty() || limit <= 0) {
            return List.of();
        }

        if (query.isConstrained()) {
            return retrieveConstrained(query, allowedRoles, limit);
        }
        if (!query.isSafeForStandardRetrieval()) {
            log.debug("Skipping unsafe or empty standard retrieval representation for query '{}'.", query.rawQuery());
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>();

        // Tier 1: exact phrase. Single-term phrase and AND queries are equivalent,
        // so skip the phrase round trip for a one-term query.
        if (query.hasPhrase() && query.positiveTerms().size() > 1) {
            candidates.addAll(executeTier(
                    Tier.PHRASE,
                    query.exactPhraseWebsearch(),
                    "",
                    allowedRoles,
                    limit
            ));
            if (mergeCandidates(candidates, limit).size() >= limit) {
                return mergeCandidates(candidates, limit);
            }
        }

        // Tier 2: all meaningful positive terms must occur in the same chunk.
        if (StringUtils.hasText(query.booleanAndQuery())) {
            candidates.addAll(executeTier(
                    Tier.AND,
                    query.booleanAndQuery(),
                    "",
                    allowedRoles,
                    limit
            ));
            if (mergeCandidates(candidates, limit).size() >= limit) {
                return mergeCandidates(candidates, limit);
            }
        }

        // Tier 3: weighted OR is intentionally restricted to ordinary positive
        // queries. The OR query contributes ranking, but a separate AND anchor
        // predicate requires both selected discriminative terms to occur in the
        // same chunk. This prevents a single generic word from opening fallback.
        if (StringUtils.hasText(query.orQuery()) && query.positiveTerms().size() > 1
                && mergeCandidates(candidates, limit).size() < limit) {
            String anchorQuery = buildAnchorAndQuery(query.positiveTerms());
            if (StringUtils.hasText(anchorQuery)) {
                candidates.addAll(executeTier(
                        Tier.WEIGHTED_OR,
                        query.orQuery(),
                        anchorQuery,
                        allowedRoles,
                        limit
                ));
            }
        }

        List<RetrievedChunk> results = mergeCandidates(candidates, limit);
        log.info("FTS multi-tier retrieval completed for query '{}'. phrase/AND/OR candidates={}, unique results={}",
                query.rawQuery(), candidates.size(), results.size());
        return results;
    }

    private List<RetrievedChunk> retrieveConstrained(
            KnowledgeQueryPreprocessor.PreprocessedQuery query,
            Set<String> allowedRoles,
            int limit
    ) {
        if (!query.constraintScopeKnown()) {
            log.info("Constrained retrieval skipped because negation/exclusion scope is ambiguous: '{}'", query.rawQuery());
            return List.of();
        }

        // Search both positive and constrained content terms as ordinary positive
        // FTS anchors. Do not include operator words (not/without/except), and do
        // not convert negated content into PostgreSQL negative tsquery operators.
        LinkedHashSet<String> contentTerms = new LinkedHashSet<>(query.positiveTerms());
        contentTerms.addAll(query.excludedScopeTerms());
        String safeAnchorQuery = String.join(" ", contentTerms);
        if (!StringUtils.hasText(safeAnchorQuery)) {
            return List.of();
        }

        List<Candidate> candidates = new ArrayList<>(
                executeTier(Tier.AND, safeAnchorQuery, "", allowedRoles, limit));
        List<RetrievedChunk> results = mergeCandidates(candidates, limit);

        // This is deliberately NOT the preprocessor's generic OR fallback. It is
        // a constrained-anchor pass: all scoped content terms remain represented,
        // the negation/exclusion metadata is retained, and the selected positive
        // content anchors must both match. The returned chunks provide
        // evidence for answering the original negative question; they are not
        // interpreted as satisfying the negated condition.
        if (results.size() < limit) {
            String constrainedOrQuery = String.join(" OR ", contentTerms);
            List<String> anchorTerms = query.positiveTerms().isEmpty()
                    ? new ArrayList<>(contentTerms)
                    : query.positiveTerms();
            String anchorQuery = buildAnchorAndQuery(anchorTerms);
            if (StringUtils.hasText(constrainedOrQuery) && StringUtils.hasText(anchorQuery)) {
                candidates.addAll(executeTier(
                        Tier.CONSTRAINED_ANCHOR,
                        constrainedOrQuery,
                        anchorQuery,
                        allowedRoles,
                        limit
                ));
                results = mergeCandidates(candidates, limit);
            }
        }

        log.info("Constrained FTS retrieval completed for query '{}'. content terms={}, candidates={}, results={}",
                query.rawQuery(), contentTerms.size(), candidates.size(), results.size());
        return results;
    }

    private List<Candidate> executeTier(
            Tier tier,
            String queryText,
            String anchorQuery,
            Set<String> allowedRoles,
            int limit
    ) {
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        String tsQueryExpression = switch (tier) {
            case PHRASE, WEIGHTED_OR, CONSTRAINED_ANCHOR -> "websearch_to_tsquery('english', :queryText)";
            case AND -> "plainto_tsquery('english', :queryText)";
        };
        boolean anchoredTier = tier == Tier.WEIGHTED_OR || tier == Tier.CONSTRAINED_ANCHOR;
        // anchorQuery is space-delimited (websearch AND semantics), not OR.
        String anchorExpression = anchoredTier
                ? "websearch_to_tsquery('english', :anchorQuery)"
                : "NULL::tsquery";
        String scoreExpression = anchoredTier
                ? "ts_rank_cd(c.chunk_vector, q.tsq, 5) + " + OR_ANCHOR_WEIGHT
                    + " * ts_rank_cd(c.chunk_vector, q.anchor_tsq, 5)"
                : "ts_rank_cd(c.chunk_vector, q.tsq, 5)";
        String additionalPredicate = anchoredTier
                ? "AND c.chunk_vector @@ q.anchor_tsq"
                : "";

        String sql = "WITH search_query AS (SELECT " + tsQueryExpression + " AS tsq, "
                + anchorExpression + " AS anchor_tsq)\n"
                + SELECT_FROM.formatted(scoreExpression, additionalPredicate);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryText", queryText.trim())
                .addValue("anchorQuery", anchorQuery == null ? "" : anchorQuery.trim())
                .addValue("allowedRoles", allowedRoles)
                .addValue("limit", limit);

        try {
            return namedJdbcTemplate.query(sql, params, (rs, rowNum) -> {
                RetrievedChunk chunk = new RetrievedChunk(
                        rs.getLong("chunk_id"),
                        (UUID) rs.getObject("document_id"),
                        rs.getString("document_code"),
                        rs.getString("document_title"),
                        rs.getInt("document_version"),
                        rs.getString("section_title"),
                        rs.getString("chunk_text"),
                        rs.getInt("token_count"),
                        rs.getDouble("relevance_score")
                );
                return new Candidate(chunk, rs.getInt("chunk_index"), tier, rs.getDouble("relevance_score"));
            });
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("PostgreSQL FTS {} tier failed for query '{}': {}", tier, queryText, e.getMessage(), e);
            throw new KnowledgeRetrievalException(
                    "Database query failed during " + tier + " knowledge retrieval: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected PostgreSQL FTS {} tier failure for query '{}': {}", tier, queryText, e.getMessage(), e);
            throw new KnowledgeRetrievalException(
                    "Knowledge retrieval encountered an unexpected " + tier + " failure: " + e.getMessage(), e);
        }
    }

    static String buildAnchorAndQuery(List<String> positiveTerms) {
        if (positiveTerms == null) {
            return "";
        }
        return positiveTerms.stream()
                .filter(StringUtils::hasText)
                .map(term -> term.trim().toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed()
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .limit(DEFAULT_ANCHOR_TERM_COUNT)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    /**
     * Merges tier results deterministically. A chunk found in a stronger tier
     * keeps that tier's rank; duplicates from later tiers never consume extra slots.
     */
    static List<RetrievedChunk> mergeCandidates(List<Candidate> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        Comparator<Candidate> ordering = Comparator
                .comparingInt((Candidate candidate) -> candidate.tier().priority())
                .thenComparing(Comparator.comparingDouble(Candidate::score).reversed())
                .thenComparingInt(Candidate::chunkIndex)
                .thenComparingLong(candidate -> candidate.chunk().chunkId());

        Map<Long, Candidate> bestByChunkId = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.chunk() == null) {
                continue;
            }
            bestByChunkId.merge(candidate.chunk().chunkId(), candidate,
                    (existing, incoming) -> ordering.compare(existing, incoming) <= 0 ? existing : incoming);
        }

        return bestByChunkId.values().stream()
                .sorted(ordering)
                .limit(limit)
                .map(Candidate::chunk)
                .toList();
    }

    enum Tier {
        PHRASE(0),
        AND(1),
        CONSTRAINED_ANCHOR(2),
        WEIGHTED_OR(3);

        private final int priority;

        Tier(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }

    record Candidate(RetrievedChunk chunk, int chunkIndex, Tier tier, double score) {}

    @Override
    public String getEngineType() {
        return "POSTGRES_FTS_LEXICAL";
    }
}
