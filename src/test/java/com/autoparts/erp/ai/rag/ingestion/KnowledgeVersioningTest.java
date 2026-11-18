package com.autoparts.erp.ai.rag.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KnowledgeVersioningTest {

    private JdbcTemplate jdbcTemplate;
    private KnowledgeIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        TextDocumentParser textParser = new TextDocumentParser();
        ingestionService = new KnowledgeIngestionService(
                jdbcTemplate,
                List.of(textParser),
                new DocumentChunker(500, 50),
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Initial ingestion creates version 1 active")
    void shouldCreateVersion1OnInitialIngestion() {
        when(jdbcTemplate.queryForList(anyString(), eq("DOC-GUIDE-01"))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("DOC-GUIDE-01")))
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "DOC-GUIDE-01",
                "ERP Guide",
                "MANUAL",
                "TEXT",
                "PUBLIC",
                "guide.txt",
                "Initial version 1 content.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );

        KnowledgeIngestionService.IngestionResult result = ingestionService.ingestDocument(request);

        assertNotNull(result);
        assertFalse(result.isDuplicate());
        assertEquals(1, result.version());
        assertEquals("DOC-GUIDE-01", result.documentCode());
        assertTrue(result.chunkCount() > 0);

        // Verify deactivation query was called
        verify(jdbcTemplate).update(
                contains("UPDATE knowledge_documents SET active = FALSE"),
                eq("DOC-GUIDE-01")
        );
    }

    @Test
    @DisplayName("Second ingestion with modified content creates version 2 and deactivates version 1")
    void shouldCreateVersion2AndDeactivateVersion1() {
        // v1 already exists with different hash
        UUID v1Id = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq("DOC-GUIDE-01"))).thenReturn(List.of(
                Map.of(
                        "id", v1Id,
                        "version", 1,
                        "file_hash_sha256", "different_old_hash_12345"
                )
        ));
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("DOC-GUIDE-01")))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "DOC-GUIDE-01",
                "ERP Guide v2",
                "MANUAL",
                "TEXT",
                "PUBLIC",
                "guide.txt",
                "Revised version 2 content with new procedures.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );

        KnowledgeIngestionService.IngestionResult result = ingestionService.ingestDocument(request);

        assertNotNull(result);
        assertFalse(result.isDuplicate());
        assertEquals(2, result.version());

        // Verify atomic deactivation of previous document version and its chunks
        verify(jdbcTemplate).update(
                eq("UPDATE knowledge_documents SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE document_code = ? AND active = TRUE"),
                eq("DOC-GUIDE-01")
        );
        verify(jdbcTemplate).update(
                eq("UPDATE knowledge_chunks SET active = FALSE WHERE document_id IN (SELECT id FROM knowledge_documents WHERE document_code = ? AND active = FALSE)"),
                eq("DOC-GUIDE-01")
        );
    }

    @Test
    @DisplayName("Partial unique index prevents two active versions of same document code")
    void shouldFailIfUniqueActiveConstraintViolated() {
        when(jdbcTemplate.queryForList(anyString(), eq("DOC-FAIL-01"))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("DOC-FAIL-01")))
                .thenReturn(0);

        // Simulate database throwing unique index violation on uq_knowledge_documents_single_active_version
        doThrow(new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"uq_knowledge_documents_single_active_version\""
        )).when(jdbcTemplate).update(contains("INSERT INTO knowledge_documents"), any(Object[].class));

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "DOC-FAIL-01",
                "Policy",
                "POLICY",
                "TEXT",
                "PUBLIC",
                "policy.txt",
                "Content that causes duplicate active error.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );

        assertThrows(DataIntegrityViolationException.class, () -> ingestionService.ingestDocument(request));
    }

    @Test
    @DisplayName("Ingestion failure triggers rollback and leaves state intact")
    void shouldLeavePriorVersionIntactWhenChunkInsertionFails() {
        when(jdbcTemplate.queryForList(anyString(), eq("DOC-TX-01"))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("DOC-TX-01"))).thenReturn(1);

        // Document insert succeeds, but batch chunk insert throws exception
        when(jdbcTemplate.update(contains("INSERT INTO knowledge_documents"), any(Object[].class))).thenReturn(1);
        doThrow(new RuntimeException("Database I/O error during chunk persistence"))
                .when(jdbcTemplate).batchUpdate(contains("INSERT INTO knowledge_chunks"), anyList());

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "DOC-TX-01",
                "Transactional Test Doc",
                "POLICY",
                "TEXT",
                "PUBLIC",
                "test.txt",
                "Valid policy text that should fail on chunk persistence.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ingestionService.ingestDocument(request));
        assertTrue(ex.getMessage().contains("Database I/O error during chunk persistence"));
    }

    @Test
    @DisplayName("Acquires parameterized transactional advisory lock on document code")
    void shouldAcquireParameterizedAdvisoryLock() {
        when(jdbcTemplate.queryForList(anyString(), eq("DOC-LOCK-01"))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("DOC-LOCK-01"))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "DOC-LOCK-01",
                "Lock Test Doc",
                "POLICY",
                "TEXT",
                "PUBLIC",
                "lock.txt",
                "Lock test content.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );

        ingestionService.ingestDocument(request);

        verify(jdbcTemplate).execute(
                eq("SELECT pg_advisory_xact_lock(hashtext(?)::bigint)"),
                any(PreparedStatementCallback.class)
        );
    }

    @Test
    @DisplayName("Advisory lock failure fails transaction and throws IllegalStateException")
    void shouldThrowIllegalStateExceptionOnLockFailure() {
        when(jdbcTemplate.execute(
                eq("SELECT pg_advisory_xact_lock(hashtext(?)::bigint)"),
                any(PreparedStatementCallback.class)
        )).thenThrow(new CannotAcquireLockException("Could not obtain lock on relation"));

        KnowledgeIngestionService.IngestionRequest request = new KnowledgeIngestionService.IngestionRequest(
                "DOC-LOCK-FAIL",
                "Lock Fail Doc",
                "POLICY",
                "TEXT",
                "PUBLIC",
                "fail.txt",
                "Content that will fail on lock.".getBytes(StandardCharsets.UTF_8),
                Map.of(),
                "admin"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ingestionService.ingestDocument(request));
        assertTrue(ex.getMessage().contains("Failed to acquire advisory lock for documentCode: DOC-LOCK-FAIL"));
    }
}
