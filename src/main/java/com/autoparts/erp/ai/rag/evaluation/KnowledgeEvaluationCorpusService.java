package com.autoparts.erp.ai.rag.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.autoparts.erp.ai.rag.ingestion.KnowledgeIngestionService;
import com.autoparts.erp.ai.rag.ingestion.KnowledgeIngestionService.IngestionRequest;
import com.autoparts.erp.ai.rag.ingestion.KnowledgeIngestionService.IngestionResult;

/**
 * Explicit, opt-in loader for the synthetic Stage 3C Phase 5 evaluation corpus.
 * Intended only for local development databases; it is disabled by default.
 */
@Service
public class KnowledgeEvaluationCorpusService {

    private static final String CORPUS_LABEL =
            "SYNTHETIC_EVALUATION_CORPUS_NOT_APPROVED_PRODUCTION_POLICY";

    private static final List<CorpusDocument> DOCUMENTS = List.of(
            new CorpusDocument("POL-WAR-001", "Automotive Battery Warranty Policy",
                    "POLICY", "MARKDOWN", "PUBLIC", "POL-WAR-001.md"),
            new CorpusDocument("POL-RET-001", "Automotive Replacement Parts Return Policy",
                    "POLICY", "PDF", "PUBLIC", "POL-RET-001.pdf"),
            new CorpusDocument("FAQ-GST-001", "Alternator HSN and GST FAQ",
                    "FAQ", "MARKDOWN", "ROLE_CASHIER", "FAQ-GST-001.md"),
            new CorpusDocument("SOP-CSH-001", "Cash Register Opening SOP",
                    "SOP", "TEXT", "ROLE_CASHIER", "SOP-CSH-001.txt")
    );

    private final KnowledgeIngestionService ingestionService;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final Path corpusDirectory;

    public KnowledgeEvaluationCorpusService(
            KnowledgeIngestionService ingestionService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.ai.live-evaluation.corpus.enabled:false}") boolean enabled,
            @Value("${app.ai.live-evaluation.corpus.path:src/test/resources/knowledge}") String corpusPath
    ) {
        this.ingestionService = ingestionService;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.corpusDirectory = Path.of(corpusPath).toAbsolutePath().normalize();
    }

    /**
     * Loads or refreshes the fixed synthetic corpus. Ingestion itself is idempotent
     * for identical file contents and versioned when fixture contents change.
     */
    @Transactional
    public SeedReport seed() {
        requireEnabled();
        Path realDirectory;
        try {
            realDirectory = corpusDirectory.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Evaluation corpus directory does not exist or is unreadable: "
                    + corpusDirectory, exception);
        }

        var results = DOCUMENTS.stream().map(document -> {
            Path candidate = realDirectory.resolve(document.fileName()).normalize();
            if (!candidate.startsWith(realDirectory)) {
                throw new IllegalStateException("Invalid evaluation corpus fixture path: " + document.fileName());
            }
            try {
                Path realFile = candidate.toRealPath();
                if (!realFile.startsWith(realDirectory) || !Files.isRegularFile(realFile)) {
                    throw new IllegalStateException("Evaluation fixture is not a regular file inside corpus directory: "
                            + document.fileName());
                }
                byte[] bytes = Files.readAllBytes(realFile);
                IngestionRequest request = new IngestionRequest(
                        document.documentCode(),
                        document.title(),
                        document.documentType(),
                        document.contentFormat(),
                        document.requiredRole(),
                        document.fileName(),
                        bytes,
                        Map.of(
                                "corpus", "STAGE_3C_PHASE_5",
                                "classification", "SYNTHETIC",
                                "corpusLabel", CORPUS_LABEL,
                                "approvedForProduction", false
                        ),
                        "LOCAL_EVALUATION_CORPUS"
                );
                return ingestionService.ingestDocument(request);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read evaluation fixture: " + document.fileName(), exception);
            }
        }).toList();

        return new SeedReport(CORPUS_LABEL, results, getStatus());
    }

    public CorpusStatus getStatus() {
        List<DocumentStatus> documents = jdbcTemplate.query(
                """
                SELECT d.document_code,
                       d.required_role,
                       d.active,
                       COUNT(c.id) FILTER (WHERE c.active = TRUE) AS active_chunks
                FROM knowledge_documents d
                LEFT JOIN knowledge_chunks c ON c.document_id = d.id
                WHERE d.active = TRUE
                  AND d.document_code IN ('POL-WAR-001', 'POL-RET-001', 'FAQ-GST-001', 'SOP-CSH-001')
                GROUP BY d.document_code, d.required_role, d.active
                ORDER BY d.document_code
                """,
                (rs, rowNum) -> new DocumentStatus(
                        rs.getString("document_code"),
                        rs.getString("required_role"),
                        rs.getBoolean("active"),
                        rs.getInt("active_chunks")
                )
        );
        int activeDocuments = (int) documents.stream().filter(DocumentStatus::active).count();
        int activeChunks = documents.stream().filter(DocumentStatus::active)
                .mapToInt(DocumentStatus::activeChunks).sum();
        return new CorpusStatus(CORPUS_LABEL, DOCUMENTS.size(), documents.size(), activeDocuments,
                activeChunks, documents);
    }

    /** Fails closed before paid evaluation calls when the fixed corpus is incomplete. */
    public void requireReady() {
        CorpusStatus status = getStatus();
        Map<String, String> expectedRoles = Map.of(
                "POL-WAR-001", "PUBLIC",
                "POL-RET-001", "PUBLIC",
                "FAQ-GST-001", "ROLE_CASHIER",
                "SOP-CSH-001", "ROLE_CASHIER"
        );
        boolean documentsReady = expectedRoles.entrySet().stream().allMatch(expected ->
                status.documents().stream().anyMatch(actual ->
                        actual.documentCode().equals(expected.getKey())
                                && actual.active()
                                && actual.activeChunks() > 0
                                && actual.requiredRole().equals(expected.getValue())));
        if (!documentsReady || status.activeDocumentCount() != DOCUMENTS.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Live evaluation corpus is not ready. Expected 4 active documents with active chunks and "
                            + "roles PUBLIC, PUBLIC, ROLE_CASHIER, ROLE_CASHIER. Current status: " + status);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Evaluation corpus loading is disabled. Enable app.ai.live-evaluation.corpus.enabled explicitly.");
        }
    }

    private record CorpusDocument(
            String documentCode,
            String title,
            String documentType,
            String contentFormat,
            String requiredRole,
            String fileName
    ) {}

    public record DocumentStatus(String documentCode, String requiredRole, boolean active, int activeChunks) {}

    public record CorpusStatus(
            String corpusLabel,
            int expectedDocumentCount,
            int foundDocumentCount,
            int activeDocumentCount,
            int activeChunkCount,
            List<DocumentStatus> documents
    ) {}

    public record SeedReport(String corpusLabel, List<IngestionResult> ingestionResults, CorpusStatus status) {}
}
