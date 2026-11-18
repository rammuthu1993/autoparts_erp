package com.autoparts.erp.ai.rag.retrieval;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KnowledgeRetrievalEngineTest {

    private NamedParameterJdbcTemplate namedJdbcTemplate;
    private PostgresFtsRetrievalEngine engine;

    @BeforeEach
    void setUp() {
        namedJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        engine = new PostgresFtsRetrievalEngine(namedJdbcTemplate);
    }

    @Test
    @DisplayName("Engine generates expected FTS query with websearch_to_tsquery, rank flag 5, double active, and role pre-filtering")
    @SuppressWarnings("unchecked")
    void shouldGenerateCorrectFtsSqlQueryAndParameters() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        UUID docId = UUID.randomUUID();
        when(namedJdbcTemplate.query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of(new RetrievedChunk(
                        101L,
                        docId,
                        "POL-RET-01",
                        "Return Policy",
                        1,
                        "30-day Window",
                        "Parts may be returned within 30 days.",
                        45,
                        0.88
                )));

        List<RetrievedChunk> results = engine.retrieve("warranty return rules", Set.of("ROLE_CASHIER", "PUBLIC"), 4);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("POL-RET-01", results.getFirst().documentCode());

        String sql = sqlCaptor.getValue();
        // 1. websearch_to_tsquery('english', :queryText)
        assertTrue(sql.contains("websearch_to_tsquery('english', :queryText)"),
                "SQL must use websearch_to_tsquery with 'english' configuration");

        // 2. ts_rank_cd(..., 5) combining flag 1 (length norm) and flag 4 (extent distance)
        assertTrue(sql.contains("ts_rank_cd(c.chunk_vector, websearch_to_tsquery('english', :queryText), 5)"),
                "SQL must use ts_rank_cd with normalization flag 5");

        // 3. Double active filtering: c.active = TRUE AND d.active = TRUE
        assertTrue(sql.contains("c.active = TRUE"), "SQL must filter on chunk active = TRUE");
        assertTrue(sql.contains("d.active = TRUE"), "SQL must filter on document active = TRUE");

        // 4. Server-side role pre-filtering: d.required_role IN (:allowedRoles)
        assertTrue(sql.contains("d.required_role IN (:allowedRoles)"),
                "SQL must enforce server-side role filtering at query boundary");

        // 5. Verify parameters
        MapSqlParameterSource params = paramCaptor.getValue();
        assertEquals("warranty return rules", params.getValue("queryText"));
        assertEquals(Set.of("ROLE_CASHIER", "PUBLIC"), params.getValue("allowedRoles"));
        assertEquals(4, params.getValue("limit"));
    }

    @Test
    @DisplayName("Engine returns empty list and avoids DB execution on empty query or empty roles")
    void shouldReturnEmptyListWithoutDbCallOnInvalidInputs() {
        assertTrue(engine.retrieve("", Set.of("PUBLIC"), 4).isEmpty());
        assertTrue(engine.retrieve("   ", Set.of("PUBLIC"), 4).isEmpty());
        assertTrue(engine.retrieve("query", Set.of(), 4).isEmpty());
        assertTrue(engine.retrieve("query", null, 4).isEmpty());
        assertTrue(engine.retrieve("query", Set.of("PUBLIC"), 0).isEmpty());

        verify(namedJdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @DisplayName("Engine reports POSTGRES_FTS_LEXICAL type")
    void shouldReportPostgresFtsLexicalEngineType() {
        assertEquals("POSTGRES_FTS_LEXICAL", engine.getEngineType());
    }

    @Test
    @DisplayName("Engine wraps DataAccessException in KnowledgeRetrievalException")
    @SuppressWarnings("unchecked")
    void shouldWrapDataAccessExceptionInKnowledgeRetrievalException() {
        when(namedJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("Simulated database timeout"));

        assertThrows(KnowledgeRetrievalException.class, () ->
                engine.retrieve("valid query", Set.of("PUBLIC"), 5)
        );
    }
}
