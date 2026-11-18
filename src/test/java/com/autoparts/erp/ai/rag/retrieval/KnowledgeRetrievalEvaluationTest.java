package com.autoparts.erp.ai.rag.retrieval;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.autoparts.erp.ai.rag.ingestion.DocumentChunker;
import com.autoparts.erp.ai.rag.ingestion.GeneratePdfCorpusFixture;
import com.autoparts.erp.ai.rag.ingestion.PdfDocumentParser;
import com.autoparts.erp.ai.rag.ingestion.TextDocumentParser;
import com.autoparts.erp.ai.rag.model.RetrievedChunk;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 3C Phase 3: Comprehensive FTS Retrieval Evaluation & Precision Benchmark.
 *
 * <p>Evaluates baseline performance of {@link PostgresFtsRetrievalEngine} against PostgreSQL 18
 * using the 4 representative corpus documents (33 chunks) and the 28-case evaluation dataset.
 * Implements strict run-specific document code generation and UUID-based cleanup to guarantee
 * complete isolation from pre-existing development database records.</p>
 */
class KnowledgeRetrievalEvaluationTest {

    private static String dbUrl = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static String dbUser = System.getProperty("DB_USERNAME", System.getenv("DB_USERNAME"));
    private static String dbPass = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));

    private static final boolean MIGRATION_OPT_IN = Boolean.parseBoolean(
            System.getProperty("test.db.migrate", System.getenv().getOrDefault("TEST_DB_MIGRATE", "false"))
    );

    private static boolean postgresAvailable = false;
    private static SingleConnectionDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbcTemplate;
    private static PostgresFtsRetrievalEngine engine;
    private static List<EvaluationCase> evaluationCases = List.of();

    // Run-specific document isolation maps
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private static final String PHASE_3_DATASET_SHA256 =
            "96259797f23c595a95857eef18345aff06478681fe37ef0d7e12df0993069030";
    private static final Map<String, String> fixtureToGeneratedCodeMap = new HashMap<>();
    private static final Map<String, String> generatedToFixtureCodeMap = new HashMap<>();
    private static final Map<String, String> docCodeToRequiredRoleMap = new HashMap<>();
    private static final Map<String, UUID> seededDocIds = new HashMap<>();
    private static final Map<Long, Integer> chunkIdToIndexMap = new HashMap<>();
    private static final Map<Long, String> chunkIdToFixtureCodeMap = new HashMap<>();

    private static final TextDocumentParser textParser = new TextDocumentParser();
    private static final PdfDocumentParser pdfParser = new PdfDocumentParser();
    private static final DocumentChunker chunker = new DocumentChunker(500, 50);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public record EvaluationCase(
            String testCaseId,
            String category,
            String question,
            String queryText,
            List<String> expectedDocumentCodes,
            Map<String, List<Integer>> targetChunkIndices,
            List<String> expectedSectionKeywords,
            String userRole,
            String expectedOutcome,
            int groundTruthRelevantCount,
            String explanation
    ) {}

    public record QueryEvaluationResult(
            EvaluationCase testCase,
            List<RetrievedChunk> retrievedTop5,
            boolean documentHit,
            boolean sectionHit,
            int firstRelevantRank,
            double precisionAt1Fixed,
            double precisionAt3Fixed,
            double precisionAt5Fixed,
            double precisionAt1Adaptive,
            double precisionAt3Adaptive,
            double precisionAt5Adaptive,
            double recallAt1,
            double recallAt3,
            double recallAt5,
            double reciprocalRank,
            boolean isNegativeCorrect,
            boolean roleIsolationMaintained,
            long latencyNanos
    ) {}

    record HostPort(String host, int port) {}

    private static final Pattern JDBC_PG_HOST_PORT_PATTERN =
            Pattern.compile("^(?:jdbc:)?postgresql://(?:[^/@]+@)?(?:\\[([^\\]]+)\\]|([^:/?#]+))(?::([0-9]+))?");

    static HostPort parseHostAndPort(String url) {
        if (url == null || url.isBlank()) {
            return new HostPort("localhost", 5432);
        }
        Matcher matcher = JDBC_PG_HOST_PORT_PATTERN.matcher(url.trim());
        if (matcher.find()) {
            String host = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int port = 5432;
            if (matcher.group(3) != null) {
                try {
                    port = Integer.parseInt(matcher.group(3));
                } catch (NumberFormatException ignored) {
                }
            }
            if (host != null && !host.isBlank()) {
                return new HostPort(host, port);
            }
        }
        return new HostPort("localhost", 5432);
    }

    static void validateSafeDatabaseTarget(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Database URL is null or empty");
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("prod") || lowerUrl.contains("production") || lowerUrl.contains("live-db")) {
            throw new IllegalStateException("Refusing to execute integration test against production database target: " + url);
        }
        HostPort hostPort = parseHostAndPort(url);
        String host = hostPort.host().toLowerCase();
        boolean isLoopback = "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
        boolean allowRemote = Boolean.parseBoolean(
                System.getProperty("test.allow.remote.db", System.getenv().getOrDefault("TEST_ALLOW_REMOTE_DB", "false"))
        );
        if (!isLoopback && !allowRemote) {
            throw new IllegalStateException("Refusing to execute integration test against non-local database target without explicit override: " + url);
        }
    }

    private static void resolveCredentials() {
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://localhost:5432/autoparts_erp";
        }
        if (dbUser == null || dbUser.isBlank()) {
            dbUser = "postgres";
        }
        if (dbPass == null || dbPass.isBlank()) {
            Path secretsPath = Path.of("config", "application-secrets.properties");
            if (Files.isRegularFile(secretsPath)) {
                try {
                    Properties props = new Properties();
                    try (InputStream in = Files.newInputStream(secretsPath)) {
                        props.load(in);
                    }
                    if (props.containsKey("DB_PASSWORD")) {
                        dbPass = props.getProperty("DB_PASSWORD");
                    }
                    if (props.containsKey("DB_USERNAME") && "postgres".equals(dbUser)) {
                        dbUser = props.getProperty("DB_USERNAME");
                    }
                    if (props.containsKey("DB_URL") && "jdbc:postgresql://localhost:5432/autoparts_erp".equals(dbUrl)) {
                        dbUrl = props.getProperty("DB_URL");
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (dbPass == null || dbPass.isBlank()) {
            dbPass = "postgres";
        }
    }

    static boolean isServiceReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] loadFixtureBytes(String resourceName) throws IOException {
        try (InputStream in = KnowledgeRetrievalEvaluationTest.class.getClassLoader().getResourceAsStream("knowledge/" + resourceName)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        Path path = Path.of("src/test/resources/knowledge", resourceName);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        if ("POL-RET-001.pdf".equals(resourceName)) {
            return GeneratePdfCorpusFixture.createPdfBytes();
        }
        throw new IOException("Resource not found: knowledge/" + resourceName);
    }

    @BeforeAll
    static void checkPostgresAvailabilityAndSetup() {
        resolveCredentials();
        validateSafeDatabaseTarget(dbUrl);

        // Load evaluation dataset JSON
        try (InputStream in = KnowledgeRetrievalEvaluationTest.class.getClassLoader()
                .getResourceAsStream("knowledge/evaluation/retrieval-evaluation-dataset.json")) {
            if (in != null) {
                evaluationCases = objectMapper.readValue(in, new TypeReference<List<EvaluationCase>>() {});
            } else {
                Path path = Path.of("src/test/resources/knowledge/evaluation/retrieval-evaluation-dataset.json");
                if (Files.exists(path)) {
                    evaluationCases = objectMapper.readValue(Files.readAllBytes(path), new TypeReference<List<EvaluationCase>>() {});
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load evaluation dataset: " + e.getMessage(), e);
        }

        // Validate dataset consistency on load
        for (EvaluationCase tc : evaluationCases) {
            validateTestCaseConsistency(tc);
        }

        // Initialize bidirectional document code mapping
        initDocumentCodeMappings(RUN_ID);

        HostPort hostPort = parseHostAndPort(dbUrl);
        if (!isServiceReachable(hostPort.host(), hostPort.port(), 1000)) {
            postgresAvailable = false;
            return;
        }

        try {
            // Validate credentials
            try (Connection probeConn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                // Connection and credentials valid
            }

            if (MIGRATION_OPT_IN) {
                Flyway flyway = Flyway.configure()
                        .dataSource(dbUrl, dbUser, dbPass)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(true)
                        .target("32")
                        .load();
                flyway.migrate();
            }

            // Create managed SingleConnectionDataSource with suppressClose=true
            dataSource = new SingleConnectionDataSource(dbUrl, dbUser, dbPass, true);
            jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

            Integer tableCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'knowledge_documents'",
                    Integer.class
            );
            if (tableCount == null || tableCount == 0) {
                postgresAvailable = false;
                return;
            }

            engine = new PostgresFtsRetrievalEngine(jdbcTemplate);
            seedCorpus();
            postgresAvailable = true;
        } catch (Throwable t) {
            postgresAvailable = false;
            // Common setup-failure cleanup path for ALL exceptions:
            try {
                cleanUpSeededDocuments();
            } catch (Throwable cleanupEx) {
                t.addSuppressed(cleanupEx);
            }
            if (dataSource != null) {
                try {
                    dataSource.destroy();
                } catch (Throwable dsEx) {
                    t.addSuppressed(dsEx);
                }
                dataSource = null;
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            if (t instanceof Error err) {
                throw err;
            }
            throw new RuntimeException("Setup failed: " + t.getMessage(), t);
        }
    }

    static void initDocumentCodeMappings(String runId) {
        fixtureToGeneratedCodeMap.put("POL-WAR-001", "EVAL_WAR_" + runId);
        fixtureToGeneratedCodeMap.put("SOP-CSH-001", "EVAL_CSH_" + runId);
        fixtureToGeneratedCodeMap.put("POL-RET-001", "EVAL_RET_" + runId);
        fixtureToGeneratedCodeMap.put("FAQ-GST-001", "EVAL_GST_" + runId);

        generatedToFixtureCodeMap.put("EVAL_WAR_" + runId, "POL-WAR-001");
        generatedToFixtureCodeMap.put("EVAL_CSH_" + runId, "SOP-CSH-001");
        generatedToFixtureCodeMap.put("EVAL_RET_" + runId, "POL-RET-001");
        generatedToFixtureCodeMap.put("EVAL_GST_" + runId, "FAQ-GST-001");

        docCodeToRequiredRoleMap.put("POL-WAR-001", "PUBLIC");
        docCodeToRequiredRoleMap.put("SOP-CSH-001", "ROLE_CASHIER");
        docCodeToRequiredRoleMap.put("POL-RET-001", "PUBLIC");
        docCodeToRequiredRoleMap.put("FAQ-GST-001", "PUBLIC");

        docCodeToRequiredRoleMap.put("EVAL_WAR_" + runId, "PUBLIC");
        docCodeToRequiredRoleMap.put("EVAL_CSH_" + runId, "ROLE_CASHIER");
        docCodeToRequiredRoleMap.put("EVAL_RET_" + runId, "PUBLIC");
        docCodeToRequiredRoleMap.put("EVAL_GST_" + runId, "PUBLIC");
    }

    private static void seedCorpus() throws Exception {
        // 1. POL-WAR-001 (Markdown, 10 chunks, PUBLIC)
        byte[] warBytes = loadFixtureBytes("POL-WAR-001.md");
        String warText = textParser.extractText(warBytes);
        UUID warDocId = UUID.randomUUID();
        String warCode = fixtureToGeneratedCodeMap.get("POL-WAR-001");
        seededDocIds.put("POL-WAR-001", warDocId);

        jdbcTemplate.getJdbcTemplate().update(
                """
                INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                VALUES (?, ?, 'Auto Parts Warranty Policy', 'POLICY', 'MARKDOWN', 'PUBLIC', 1, TRUE, 'POL-WAR-001.md', ?, ?, '{}'::jsonb, 'TEST')
                """,
                warDocId, warCode, DocumentChunker.computeSha256(warBytes), warBytes.length
        );
        List<DocumentChunker.ChunkDraft> warChunks = chunker.chunk(warText, "Auto Parts Warranty Policy");
        for (DocumentChunker.ChunkDraft draft : warChunks) {
            Long chunkId = jdbcTemplate.getJdbcTemplate().queryForObject(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    warDocId, draft.chunkIndex(), draft.sectionTitle(), draft.text(), draft.estimatedTokens(),
                    DocumentChunker.computeSha256(draft.text().getBytes(StandardCharsets.UTF_8))
            );
            chunkIdToIndexMap.put(chunkId, draft.chunkIndex());
            chunkIdToFixtureCodeMap.put(chunkId, "POL-WAR-001");
        }

        // 2. SOP-CSH-001 (Text, 6 chunks, ROLE_CASHIER)
        byte[] sopBytes = loadFixtureBytes("SOP-CSH-001.txt");
        String sopText = textParser.extractText(sopBytes);
        UUID sopDocId = UUID.randomUUID();
        String sopCode = fixtureToGeneratedCodeMap.get("SOP-CSH-001");
        seededDocIds.put("SOP-CSH-001", sopDocId);

        jdbcTemplate.getJdbcTemplate().update(
                """
                INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                VALUES (?, ?, 'Daily Cash Register Closing & Reconciliation SOP', 'SOP', 'TEXT', 'ROLE_CASHIER', 1, TRUE, 'SOP-CSH-001.txt', ?, ?, '{}'::jsonb, 'TEST')
                """,
                sopDocId, sopCode, DocumentChunker.computeSha256(sopBytes), sopBytes.length
        );
        List<DocumentChunker.ChunkDraft> sopChunks = chunker.chunk(sopText, "Daily Cash Register Closing & Reconciliation SOP");
        for (DocumentChunker.ChunkDraft draft : sopChunks) {
            Long chunkId = jdbcTemplate.getJdbcTemplate().queryForObject(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    sopDocId, draft.chunkIndex(), draft.sectionTitle(), draft.text(), draft.estimatedTokens(),
                    DocumentChunker.computeSha256(draft.text().getBytes(StandardCharsets.UTF_8))
            );
            chunkIdToIndexMap.put(chunkId, draft.chunkIndex());
            chunkIdToFixtureCodeMap.put(chunkId, "SOP-CSH-001");
        }

        // 3. POL-RET-001 (PDF, 3 chunks, PUBLIC)
        byte[] retBytes = loadFixtureBytes("POL-RET-001.pdf");
        String retText = pdfParser.extractText(retBytes);
        UUID retDocId = UUID.randomUUID();
        String retCode = fixtureToGeneratedCodeMap.get("POL-RET-001");
        seededDocIds.put("POL-RET-001", retDocId);

        jdbcTemplate.getJdbcTemplate().update(
                """
                INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                VALUES (?, ?, 'Return and Core Exchange Policy', 'POLICY', 'PDF', 'PUBLIC', 1, TRUE, 'POL-RET-001.pdf', ?, ?, '{}'::jsonb, 'TEST')
                """,
                retDocId, retCode, DocumentChunker.computeSha256(retBytes), retBytes.length
        );
        List<DocumentChunker.ChunkDraft> retChunks = chunker.chunk(retText, "Return and Core Exchange Policy");
        for (DocumentChunker.ChunkDraft draft : retChunks) {
            Long chunkId = jdbcTemplate.getJdbcTemplate().queryForObject(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    retDocId, draft.chunkIndex(), draft.sectionTitle(), draft.text(), draft.estimatedTokens(),
                    DocumentChunker.computeSha256(draft.text().getBytes(StandardCharsets.UTF_8))
            );
            chunkIdToIndexMap.put(chunkId, draft.chunkIndex());
            chunkIdToFixtureCodeMap.put(chunkId, "POL-RET-001");
        }

        // 4. FAQ-GST-001 (Markdown, 14 chunks, PUBLIC)
        byte[] gstBytes = loadFixtureBytes("FAQ-GST-001.md");
        String gstText = textParser.extractText(gstBytes);
        UUID gstDocId = UUID.randomUUID();
        String gstCode = fixtureToGeneratedCodeMap.get("FAQ-GST-001");
        seededDocIds.put("FAQ-GST-001", gstDocId);

        jdbcTemplate.getJdbcTemplate().update(
                """
                INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                VALUES (?, ?, 'ERP Billing and GST Clarification FAQ', 'FAQ', 'MARKDOWN', 'PUBLIC', 1, TRUE, 'FAQ-GST-001.md', ?, ?, '{}'::jsonb, 'TEST')
                """,
                gstDocId, gstCode, DocumentChunker.computeSha256(gstBytes), gstBytes.length
        );
        List<DocumentChunker.ChunkDraft> gstChunks = chunker.chunk(gstText, "ERP Billing and GST Clarification FAQ");
        for (DocumentChunker.ChunkDraft draft : gstChunks) {
            Long chunkId = jdbcTemplate.getJdbcTemplate().queryForObject(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, ?, ?, ?, ?, ?, TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    gstDocId, draft.chunkIndex(), draft.sectionTitle(), draft.text(), draft.estimatedTokens(),
                    DocumentChunker.computeSha256(draft.text().getBytes(StandardCharsets.UTF_8))
            );
            chunkIdToIndexMap.put(chunkId, draft.chunkIndex());
            chunkIdToFixtureCodeMap.put(chunkId, "FAQ-GST-001");
        }
    }

    /**
     * Cleans up seeded documents strictly by their recorded UUIDs.
     * Guaranteed safe: Never uses fixed document codes or DELETE without specific primary keys.
     * Only removes IDs from seededDocIds upon successful deletion.
     */
    static void cleanUpDocIds(Iterable<UUID> docIds) {
        if (jdbcTemplate == null || docIds == null) return;
        List<Exception> errors = new ArrayList<>();
        List<UUID> successfullyDeleted = new ArrayList<>();

        for (UUID docId : docIds) {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id = ?", docId);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id = ?", docId);
                successfullyDeleted.add(docId);
            } catch (Exception e) {
                errors.add(e);
            }
        }

        // Remove only successfully deleted UUIDs
        for (UUID id : successfullyDeleted) {
            seededDocIds.values().remove(id);
        }

        if (!errors.isEmpty()) {
            RuntimeException re = new RuntimeException("Errors during UUID cleanup: failed to delete " + errors.size() + " documents. Retained remaining IDs for recovery.");
            for (Exception e : errors) re.addSuppressed(e);
            throw re;
        }
    }

    static void cleanUpSeededDocuments() {
        if (!seededDocIds.isEmpty()) {
            List<UUID> toClean = new ArrayList<>(seededDocIds.values());
            cleanUpDocIds(toClean);
            chunkIdToIndexMap.clear();
            chunkIdToFixtureCodeMap.clear();
        }
    }

    @AfterAll
    static void tearDown() {
        try {
            cleanUpSeededDocuments();
        } finally {
            if (dataSource != null) {
                dataSource.destroy();
                dataSource = null;
            }
        }
    }

    /**
     * Validates that evaluation dataset entries are internally consistent.
     * Rejects discrepancies between declared groundTruthRelevantCount and targetChunkIndices.
     */
    static void validateTestCaseConsistency(EvaluationCase tc) {
        if (tc.expectedDocumentCodes() == null) {
            throw new IllegalArgumentException("expectedDocumentCodes cannot be null in " + tc.testCaseId());
        }
        if (tc.expectedDocumentCodes().isEmpty()) {
            if (tc.groundTruthRelevantCount() != 0) {
                throw new IllegalArgumentException("Negative test case " + tc.testCaseId()
                        + " must have groundTruthRelevantCount = 0, but found: " + tc.groundTruthRelevantCount());
            }
            return;
        }
        if ("NO_MATCH".equals(tc.expectedOutcome())) {
            if (tc.groundTruthRelevantCount() != 0) {
                throw new IllegalArgumentException("Role-blocked NO_MATCH case " + tc.testCaseId()
                        + " must have groundTruthRelevantCount = 0, but found: " + tc.groundTruthRelevantCount());
            }
            return;
        }

        // Positive case: validate targetChunkIndices count against groundTruthRelevantCount
        if (tc.targetChunkIndices() != null && !tc.targetChunkIndices().isEmpty()) {
            int expectedCount = 0;
            for (Map.Entry<String, List<Integer>> entry : tc.targetChunkIndices().entrySet()) {
                if (!tc.expectedDocumentCodes().contains(entry.getKey())) {
                    throw new IllegalArgumentException("targetChunkIndices contains unlisted document code " + entry.getKey()
                            + " in " + tc.testCaseId());
                }
                Set<Integer> uniqueIndices = new HashSet<>(entry.getValue());
                expectedCount += uniqueIndices.size();
            }
            if (expectedCount != tc.groundTruthRelevantCount()) {
                throw new IllegalArgumentException("Dataset inconsistency in " + tc.testCaseId()
                        + ": declared groundTruthRelevantCount (" + tc.groundTruthRelevantCount()
                        + ") does not match targetChunkIndices count (" + expectedCount + ")");
            }
        } else {
            if (tc.groundTruthRelevantCount() <= 0) {
                throw new IllegalArgumentException("Positive test case " + tc.testCaseId()
                        + " must have groundTruthRelevantCount > 0, but found: " + tc.groundTruthRelevantCount());
            }
        }
    }

    /**
     * Authoritative relevance determination:
     * 1. If targetChunkIndices are present for a document, those indices are strictly authoritative.
     * 2. Section keyword fallback is ONLY used when no explicit targetChunkIndices exist for that document.
     * 3. Never combines index matching and keyword matching with OR.
     */
    static boolean isChunkSectionRelevant(
            RetrievedChunk chunk,
            EvaluationCase tc,
            Map<Long, Integer> chunkIdToIndexMap,
            Map<Long, String> chunkIdToFixtureCodeMap,
            Map<String, String> generatedToFixtureCodeMap
    ) {
        String fixtureCode = chunkIdToFixtureCodeMap != null && chunkIdToFixtureCodeMap.containsKey(chunk.chunkId())
                ? chunkIdToFixtureCodeMap.get(chunk.chunkId())
                : (generatedToFixtureCodeMap != null ? generatedToFixtureCodeMap.getOrDefault(chunk.documentCode(), chunk.documentCode()) : chunk.documentCode());

        if (!tc.expectedDocumentCodes().contains(fixtureCode)) {
            return false;
        }

        // Authoritative rule: If targetChunkIndices are present for this document, they are authoritative
        if (tc.targetChunkIndices() != null && tc.targetChunkIndices().containsKey(fixtureCode)) {
            List<Integer> targetIndices = tc.targetChunkIndices().get(fixtureCode);
            int chunkIndex = chunkIdToIndexMap != null ? chunkIdToIndexMap.getOrDefault(chunk.chunkId(), -1) : -1;
            return targetIndices != null && targetIndices.contains(chunkIndex);
        }

        // Fallback: ONLY when no explicit targetChunkIndices are provided for this document, use section keywords
        if (tc.expectedSectionKeywords() != null && !tc.expectedSectionKeywords().isEmpty()) {
            return tc.expectedSectionKeywords().stream().anyMatch(kw ->
                    (chunk.sectionTitle() != null && chunk.sectionTitle().toLowerCase().contains(kw.toLowerCase()))
                            || (chunk.chunkText() != null && chunk.chunkText().toLowerCase().contains(kw.toLowerCase()))
            );
        }

        return true;
    }

    /**
     * Pure metric calculation implementation with default corpus role map.
     */
    static QueryEvaluationResult computeMetrics(
            EvaluationCase tc,
            List<RetrievedChunk> retrievedTop5,
            Map<Long, Integer> chunkIdToIndexMap,
            Map<Long, String> chunkIdToFixtureCodeMap,
            Map<String, String> generatedToFixtureCodeMap,
            long latencyNanos
    ) {
        return computeMetrics(tc, retrievedTop5, chunkIdToIndexMap, chunkIdToFixtureCodeMap, generatedToFixtureCodeMap, docCodeToRequiredRoleMap, latencyNanos);
    }

    /**
     * Pure metric calculation implementation with explicit document role map for fail-closed authorization verification.
     */
    static QueryEvaluationResult computeMetrics(
            EvaluationCase tc,
            List<RetrievedChunk> retrievedTop5,
            Map<Long, Integer> chunkIdToIndexMap,
            Map<Long, String> chunkIdToFixtureCodeMap,
            Map<String, String> generatedToFixtureCodeMap,
            Map<String, String> docRoleMap,
            long latencyNanos
    ) {
        validateTestCaseConsistency(tc);

        // Fail-closed authorization check: verify every returned chunk's document is accessible to requesting role
        Set<String> allowedRoles = KnowledgeRetrievalService.resolveAllowedRoles(
                tc.userRole() != null ? Set.of(tc.userRole()) : Set.of("PUBLIC")
        );

        boolean unauthorizedDocumentLeaked = retrievedTop5.stream().anyMatch(chunk -> {
            String fixtureCode = chunkIdToFixtureCodeMap != null && chunkIdToFixtureCodeMap.containsKey(chunk.chunkId())
                    ? chunkIdToFixtureCodeMap.get(chunk.chunkId())
                    : (generatedToFixtureCodeMap != null ? generatedToFixtureCodeMap.getOrDefault(chunk.documentCode(), chunk.documentCode()) : chunk.documentCode());
            String requiredRole = null;
            if (docRoleMap != null) {
                requiredRole = docRoleMap.get(fixtureCode);
                if (requiredRole == null) {
                    requiredRole = docRoleMap.get(chunk.documentCode());
                }
            }
            if (requiredRole == null) {
                // Fail-closed default: unrecognized documents require highest privilege (ROLE_ADMIN)
                requiredRole = "ROLE_ADMIN";
            }
            return !allowedRoles.contains(requiredRole);
        });

        boolean roleIsolationMaintained = !unauthorizedDocumentLeaked;

        boolean isNegative = tc.expectedDocumentCodes().isEmpty();

        if (isNegative) {
            boolean isNegativeCorrect = retrievedTop5.isEmpty() && roleIsolationMaintained;
            return new QueryEvaluationResult(
                    tc, retrievedTop5, false, false, 0,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN,
                    isNegativeCorrect,
                    roleIsolationMaintained,
                    latencyNanos
            );
        }

        if ("NO_MATCH".equals(tc.expectedOutcome())) {
            boolean isCorrect = retrievedTop5.isEmpty() && roleIsolationMaintained;
            return new QueryEvaluationResult(
                    tc, retrievedTop5, false, false, 0,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN,
                    isCorrect,
                    roleIsolationMaintained,
                    latencyNanos
            );
        }

        boolean docHit = retrievedTop5.stream().anyMatch(c -> {
            String fixtureCode = chunkIdToFixtureCodeMap != null && chunkIdToFixtureCodeMap.containsKey(c.chunkId())
                    ? chunkIdToFixtureCodeMap.get(c.chunkId())
                    : (generatedToFixtureCodeMap != null ? generatedToFixtureCodeMap.getOrDefault(c.documentCode(), c.documentCode()) : c.documentCode());
            return tc.expectedDocumentCodes().contains(fixtureCode);
        });

        int firstRelevantRank = 0;
        Set<Long> uniqueRelevantTop1 = new HashSet<>();
        Set<Long> uniqueRelevantTop3 = new HashSet<>();
        Set<Long> uniqueRelevantTop5 = new HashSet<>();

        for (int i = 0; i < retrievedTop5.size(); i++) {
            RetrievedChunk chunk = retrievedTop5.get(i);
            boolean isSectionMatch = isChunkSectionRelevant(chunk, tc, chunkIdToIndexMap, chunkIdToFixtureCodeMap, generatedToFixtureCodeMap);

            if (isSectionMatch) {
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                }
                if (i < 1) uniqueRelevantTop1.add(chunk.chunkId());
                if (i < 3) uniqueRelevantTop3.add(chunk.chunkId());
                if (i < 5) uniqueRelevantTop5.add(chunk.chunkId());
            }
        }

        boolean sectionHit = firstRelevantRank > 0;
        double mrr = firstRelevantRank > 0 ? 1.0 / firstRelevantRank : 0.0;

        int totalGroundTruthRelevant = tc.groundTruthRelevantCount();

        // Precision@K (Fixed Denominator: relevant in top K / K)
        double p1Fixed = (double) uniqueRelevantTop1.size() / 1.0;
        double p3Fixed = (double) uniqueRelevantTop3.size() / 3.0;
        double p5Fixed = (double) uniqueRelevantTop5.size() / 5.0;

        // Precision@K (Adaptive Denominator: relevant in top K / min(K, totalRetrieved))
        double p1Adaptive = retrievedTop5.isEmpty() ? 0.0 : (double) uniqueRelevantTop1.size() / Math.min(1, retrievedTop5.size());
        double p3Adaptive = retrievedTop5.isEmpty() ? 0.0 : (double) uniqueRelevantTop3.size() / Math.min(3, retrievedTop5.size());
        double p5Adaptive = retrievedTop5.isEmpty() ? 0.0 : (double) uniqueRelevantTop5.size() / Math.min(5, retrievedTop5.size());

        // Recall@K (True Recall: relevant in top K / total ground truth relevant)
        // No Math.min(1.0, ...) clamping! Ground-truth matching is exact.
        double r1 = (double) uniqueRelevantTop1.size() / totalGroundTruthRelevant;
        double r3 = (double) uniqueRelevantTop3.size() / totalGroundTruthRelevant;
        double r5 = (double) uniqueRelevantTop5.size() / totalGroundTruthRelevant;

        return new QueryEvaluationResult(
                tc, retrievedTop5, docHit, sectionHit, firstRelevantRank,
                p1Fixed, p3Fixed, p5Fixed,
                p1Adaptive, p3Adaptive, p5Adaptive,
                r1, r3, r5, mrr,
                true, roleIsolationMaintained, latencyNanos
        );
    }

    static QueryEvaluationResult evaluateQuery(EvaluationCase tc) {
        Set<String> allowedRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of(tc.userRole()));
        long start = System.nanoTime();
        List<RetrievedChunk> top5 = engine.retrieve(tc.queryText(), allowedRoles, 5);
        long latencyNanos = System.nanoTime() - start;

        return computeMetrics(tc, top5, chunkIdToIndexMap, chunkIdToFixtureCodeMap, generatedToFixtureCodeMap, latencyNanos);
    }

    @Test
    @DisplayName("Phase 3 evaluation dataset remains unchanged: 28 cases and original SHA-256")
    void shouldKeepOriginalPhase3EvaluationDatasetUnchanged() throws Exception {
        try (InputStream in = KnowledgeRetrievalEvaluationTest.class.getClassLoader()
                .getResourceAsStream("knowledge/evaluation/retrieval-evaluation-dataset.json")) {
            assertNotNull(in, "Original Phase 3 evaluation dataset resource must exist");
            byte[] bytes = in.readAllBytes();
            String actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(PHASE_3_DATASET_SHA256, actualHash,
                    "Phase 3 query text, targets, roles, and ground truth must remain unchanged");
            List<EvaluationCase> cases = objectMapper.readValue(bytes, new TypeReference<List<EvaluationCase>>() {});
            assertEquals(28, cases.size(), "The original 28-query evaluation dataset must be reused unchanged");
        }
    }

    // =========================================================================
    // Lifecycle & Isolation Verification Tests
    // =========================================================================
    @Test
    @DisplayName("Lifecycle: Verify JDBC connection remains open after @BeforeAll and engine executes query")
    void shouldVerifyConnectionIsOpenAndUsable() throws SQLException {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");
        assertNotNull(dataSource, "DataSource must not be null");
        assertNotNull(jdbcTemplate, "JdbcTemplate must not be null");

        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn, "Connection must not be null");
            assertFalse(conn.isClosed(), "Connection must remain open after @BeforeAll completes");
            assertTrue(conn.isValid(1), "Connection must be valid");
        }

        // Verify retrieval engine can execute query against database
        List<RetrievedChunk> results = engine.retrieve("warranty", Set.of("PUBLIC"), 1);
        assertNotNull(results, "Retrieval result must not be null");
    }

    @Test
    @DisplayName("Lifecycle: Verify partial-seeding failure cleanup deletes all recorded UUIDs without touching existing data")
    void shouldVerifyPartialSeedingCleanupOnFailure() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        UUID testDocId1 = UUID.randomUUID();
        UUID testDocId2 = UUID.randomUUID();
        String code1 = "TEST_PARTIAL_1_" + UUID.randomUUID().toString().substring(0, 8);
        String code2 = "TEST_PARTIAL_2_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, UUID> partialMap = new HashMap<>();

        try {
            // Insert document 1
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Partial 1', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'p1.txt', 'h1', 100, '{}'::jsonb, 'TEST')
                    """,
                    testDocId1, code1
            );
            partialMap.put(code1, testDocId1);

            // Insert chunk for document 1
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Section 1', 'Partial text 1', 10, 'ch1', TRUE)
                    """,
                    testDocId1
            );

            // Insert document 2
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Partial 2', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'p2.txt', 'h2', 100, '{}'::jsonb, 'TEST')
                    """,
                    testDocId2, code2
            );
            partialMap.put(code2, testDocId2);

            // Verify both exist
            Integer countBefore = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM knowledge_documents WHERE id IN (?, ?)",
                    Integer.class, testDocId1, testDocId2
            );
            assertEquals(2, countBefore);

            // Execute cleanup on partialMap
            cleanUpDocIds(partialMap.values());

            // Verify both are completely removed
            Integer countAfter = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM knowledge_documents WHERE id IN (?, ?)",
                    Integer.class, testDocId1, testDocId2
            );
            assertEquals(0, countAfter, "All partially seeded documents must be deleted by exact UUID");

            Integer chunkCountAfter = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM knowledge_chunks WHERE document_id IN (?, ?)",
                    Integer.class, testDocId1, testDocId2
            );
            assertEquals(0, chunkCountAfter, "All chunks of partially seeded documents must be deleted");

        } finally {
            cleanUpDocIds(List.of(testDocId1, testDocId2));
        }
    }

    @Test
    @DisplayName("Unit: Verify run-specific document codes are generated and map correctly")
    void shouldVerifyRunSpecificDocumentCodeMappingAndIsolation() {
        assertNotNull(RUN_ID, "Run ID must not be null");
        assertFalse(RUN_ID.isBlank(), "Run ID must not be blank");

        assertEquals(4, fixtureToGeneratedCodeMap.size());
        assertEquals(4, generatedToFixtureCodeMap.size());

        for (Map.Entry<String, String> entry : fixtureToGeneratedCodeMap.entrySet()) {
            String fixtureCode = entry.getKey();
            String generatedCode = entry.getValue();

            assertTrue(generatedCode.endsWith(RUN_ID), "Generated code must include run ID: " + generatedCode);
            assertNotEquals(fixtureCode, generatedCode, "Generated code must differ from fixture code for isolation");
            assertEquals(fixtureCode, generatedToFixtureCodeMap.get(generatedCode), "Generated code must map back to fixture code");
        }
    }

    // =========================================================================
    // Unit Tests: Pure Metric Calculation Verification
    // =========================================================================
    @Test
    @DisplayName("Unit: Verify metric calculation for one relevant chunk retrieved at rank 1")
    void shouldCalculateMetricsForSingleRelevantChunkRetrievedAtRank1() {
        EvaluationCase tc = new EvaluationCase(
                "TEST-UNIT-001", "DIRECT_KEYWORD", "Query single", "battery warranty",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3)), List.of(),
                "PUBLIC", "MATCH_FOUND", 1, "Single relevant chunk"
        );

        UUID docId = UUID.randomUUID();
        RetrievedChunk relevantChunk = new RetrievedChunk(101L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.1 Batteries", "Battery text", 100, 0.95);
        RetrievedChunk irrelevantChunk1 = new RetrievedChunk(102L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.2 Brakes", "Brake text", 100, 0.80);
        RetrievedChunk irrelevantChunk2 = new RetrievedChunk(103L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.3 Alternators", "Alternator text", 100, 0.70);

        Map<Long, Integer> indexMap = Map.of(101L, 3, 102L, 4, 103L, 5);
        Map<Long, String> fixtureCodeMap = Map.of(101L, "POL-WAR-001", 102L, "POL-WAR-001", 103L, "POL-WAR-001");

        QueryEvaluationResult result = computeMetrics(tc, List.of(relevantChunk, irrelevantChunk1, irrelevantChunk2), indexMap, fixtureCodeMap, Map.of(), 5000L);

        assertTrue(result.documentHit());
        assertTrue(result.sectionHit());
        assertEquals(1, result.firstRelevantRank());
        assertEquals(1.0, result.reciprocalRank(), 0.001);

        // Recall@K
        assertEquals(1.0, result.recallAt1(), 0.001);
        assertEquals(1.0, result.recallAt3(), 0.001);
        assertEquals(1.0, result.recallAt5(), 0.001);

        // Precision@K Fixed
        assertEquals(1.0, result.precisionAt1Fixed(), 0.001);
        assertEquals(1.0 / 3.0, result.precisionAt3Fixed(), 0.001);
        assertEquals(1.0 / 5.0, result.precisionAt5Fixed(), 0.001);

        // Precision@K Adaptive (3 returned)
        assertEquals(1.0, result.precisionAt1Adaptive(), 0.001);
        assertEquals(1.0 / 3.0, result.precisionAt3Adaptive(), 0.001);
        assertEquals(1.0 / 3.0, result.precisionAt5Adaptive(), 0.001);
    }

    @Test
    @DisplayName("Unit: Verify metric calculation for two relevant chunks with one found in top 1")
    void shouldCalculateMetricsForTwoRelevantChunksWithOneFoundInTop1() {
        EvaluationCase tc = new EvaluationCase(
                "TEST-UNIT-002", "MULTI_TERM", "Query multi", "battery warranty tester",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3, 7)), List.of(),
                "PUBLIC", "MATCH_FOUND", 2, "Two relevant chunks"
        );

        UUID docId = UUID.randomUUID();
        RetrievedChunk chunkRank1 = new RetrievedChunk(101L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.1 Batteries", "Battery text", 100, 0.95); // index 3 (relevant)
        RetrievedChunk chunkRank2 = new RetrievedChunk(102L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.2 Brakes", "Brake text", 100, 0.80);    // index 4 (irrelevant)
        RetrievedChunk chunkRank3 = new RetrievedChunk(103L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.3 Alternators", "Alt text", 100, 0.70);   // index 5 (irrelevant)
        RetrievedChunk chunkRank4 = new RetrievedChunk(104L, docId, "POL-WAR-001", "Warranty Policy", 1, "3. Exclusions", "Exclusion text", 100, 0.60);// index 7 (relevant)

        Map<Long, Integer> indexMap = Map.of(101L, 3, 102L, 4, 103L, 5, 104L, 7);
        Map<Long, String> fixtureCodeMap = Map.of(101L, "POL-WAR-001", 102L, "POL-WAR-001", 103L, "POL-WAR-001", 104L, "POL-WAR-001");

        QueryEvaluationResult result = computeMetrics(tc, List.of(chunkRank1, chunkRank2, chunkRank3, chunkRank4), indexMap, fixtureCodeMap, Map.of(), 5000L);

        assertEquals(1, result.firstRelevantRank());
        assertEquals(1.0, result.reciprocalRank(), 0.001);

        // Recall@K: 1 found in top-1, 1 found in top-3, 2 found in top-5 out of 2 ground truth
        assertEquals(0.50, result.recallAt1(), 0.001);
        assertEquals(0.50, result.recallAt3(), 0.001);
        assertEquals(1.00, result.recallAt5(), 0.001);

        // Precision@K Fixed
        assertEquals(1.0, result.precisionAt1Fixed(), 0.001);
        assertEquals(1.0 / 3.0, result.precisionAt3Fixed(), 0.001);
        assertEquals(2.0 / 5.0, result.precisionAt5Fixed(), 0.001);
    }

    @Test
    @DisplayName("Unit: Verify metric calculation for two relevant chunks both found in top 3")
    void shouldCalculateMetricsForTwoRelevantChunksBothFoundInTop3() {
        EvaluationCase tc = new EvaluationCase(
                "TEST-UNIT-003", "MULTI_TERM", "Query multi", "battery warranty tester",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3, 7)), List.of(),
                "PUBLIC", "MATCH_FOUND", 2, "Two relevant chunks"
        );

        UUID docId = UUID.randomUUID();
        RetrievedChunk chunkRank1 = new RetrievedChunk(101L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.2 Brakes", "Brake text", 100, 0.95);    // index 4 (irrelevant)
        RetrievedChunk chunkRank2 = new RetrievedChunk(102L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.1 Batteries", "Battery text", 100, 0.85); // index 3 (relevant)
        RetrievedChunk chunkRank3 = new RetrievedChunk(103L, docId, "POL-WAR-001", "Warranty Policy", 1, "3. Exclusions", "Exclusion text", 100, 0.75);// index 7 (relevant)

        Map<Long, Integer> indexMap = Map.of(101L, 4, 102L, 3, 103L, 7);
        Map<Long, String> fixtureCodeMap = Map.of(101L, "POL-WAR-001", 102L, "POL-WAR-001", 103L, "POL-WAR-001");

        QueryEvaluationResult result = computeMetrics(tc, List.of(chunkRank1, chunkRank2, chunkRank3), indexMap, fixtureCodeMap, Map.of(), 5000L);

        assertEquals(2, result.firstRelevantRank());
        assertEquals(0.50, result.reciprocalRank(), 0.001);

        // Recall@K: 0 in top-1, 2 in top-3, 2 in top-5
        assertEquals(0.00, result.recallAt1(), 0.001);
        assertEquals(1.00, result.recallAt3(), 0.001);
        assertEquals(1.00, result.recallAt5(), 0.001);

        // Precision@K Fixed
        assertEquals(0.0, result.precisionAt1Fixed(), 0.001);
        assertEquals(2.0 / 3.0, result.precisionAt3Fixed(), 0.001);
        assertEquals(2.0 / 5.0, result.precisionAt5Fixed(), 0.001);
    }

    @Test
    @DisplayName("Unit: Verify metric calculation for negative query with zero relevant chunks")
    void shouldCalculateMetricsForNegativeQueryWithNoRelevantChunks() {
        EvaluationCase negativeCase = new EvaluationCase(
                "TEST-UNIT-004", "NEGATIVE_OUT_OF_SCOPE", "HR leave", "employee leave request",
                List.of(), Map.of(), List.of(),
                "PUBLIC", "NO_MATCH", 0, "Negative query"
        );

        // Case A: 0 results returned (True Negative)
        QueryEvaluationResult correctResult = computeMetrics(negativeCase, List.of(), Map.of(), Map.of(), Map.of(), 1000L);
        assertTrue(correctResult.isNegativeCorrect());
        assertTrue(Double.isNaN(correctResult.precisionAt1Fixed()), "Precision on negative query must be Double.NaN");
        assertTrue(Double.isNaN(correctResult.recallAt1()), "Recall on negative query must be Double.NaN");
        assertTrue(Double.isNaN(correctResult.reciprocalRank()), "Reciprocal rank on negative query must be Double.NaN");

        // Case B: 1 false-positive result returned
        RetrievedChunk fpChunk = new RetrievedChunk(999L, UUID.randomUUID(), "OTHER", "Other Doc", 1, "Other", "Some text", 50, 0.5);
        QueryEvaluationResult fpResult = computeMetrics(negativeCase, List.of(fpChunk), Map.of(), Map.of(), Map.of(), 1000L);
        assertFalse(fpResult.isNegativeCorrect());
        assertTrue(Double.isNaN(fpResult.precisionAt1Fixed()), "Precision on negative query must be Double.NaN even on false positive");
        assertTrue(Double.isNaN(fpResult.recallAt1()), "Recall on negative query must be Double.NaN even on false positive");
        assertTrue(Double.isNaN(fpResult.reciprocalRank()), "Reciprocal rank on negative query must be Double.NaN even on false positive");
    }

    @Test
    @DisplayName("Unit: Verify metric calculation for positive query with zero retrieved results")
    void shouldCalculateMetricsForPositiveQueryWithNoRetrievedResults() {
        EvaluationCase tc = new EvaluationCase(
                "TEST-UNIT-005", "DIRECT_KEYWORD", "Unmatched query", "unmatched term",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3)), List.of(),
                "PUBLIC", "MATCH_FOUND", 1, "Unmatched query"
        );

        QueryEvaluationResult result = computeMetrics(tc, List.of(), Map.of(), Map.of(), Map.of(), 1000L);
        assertFalse(result.documentHit());
        assertFalse(result.sectionHit());
        assertEquals(0, result.firstRelevantRank());
        assertEquals(0.0, result.reciprocalRank());
        assertEquals(0.0, result.recallAt1());
        assertEquals(0.0, result.recallAt3());
        assertEquals(0.0, result.recallAt5());
        assertEquals(0.0, result.precisionAt1Fixed());
        assertEquals(0.0, result.precisionAt3Fixed());
        assertEquals(0.0, result.precisionAt5Fixed());
        assertEquals(0.0, result.precisionAt1Adaptive());
        assertEquals(0.0, result.precisionAt3Adaptive());
        assertEquals(0.0, result.precisionAt5Adaptive());
    }

    @Test
    @DisplayName("Unit: Verify duplicate chunk IDs in retrieved list are counted only once")
    void shouldCountDuplicateRelevantChunkIdsOnlyOnce() {
        EvaluationCase tc = new EvaluationCase(
                "TEST-UNIT-006", "DIRECT_KEYWORD", "Duplicate query", "battery warranty",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3)), List.of(),
                "PUBLIC", "MATCH_FOUND", 1, "Duplicate chunk test"
        );

        UUID docId = UUID.randomUUID();
        // Same chunkId 101L appears twice in retrieved list
        RetrievedChunk dupChunk1 = new RetrievedChunk(101L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.1 Batteries", "Battery text", 100, 0.95);
        RetrievedChunk dupChunk2 = new RetrievedChunk(101L, docId, "POL-WAR-001", "Warranty Policy", 1, "2.1 Batteries", "Battery text", 100, 0.90);

        Map<Long, Integer> indexMap = Map.of(101L, 3);
        Map<Long, String> fixtureCodeMap = Map.of(101L, "POL-WAR-001");

        QueryEvaluationResult result = computeMetrics(tc, List.of(dupChunk1, dupChunk2), indexMap, fixtureCodeMap, Map.of(), 1000L);

        // Unique relevant chunk count is 1, not 2
        assertEquals(1.0, result.recallAt1(), 0.001);
        assertEquals(1.0, result.recallAt3(), 0.001);
        assertEquals(1.0, result.recallAt5(), 0.001);
        assertEquals(1.0, result.precisionAt1Fixed(), 0.001);
        assertEquals(1.0 / 3.0, result.precisionAt3Fixed(), 0.001);
    }

    @Test
    @DisplayName("Unit: Verify inconsistent groundTruthRelevantCount in dataset entry is rejected")
    void shouldRejectDatasetEntryWithInconsistentGroundTruthCount() {
        // targetChunkIndices has 2 chunks ([3, 7]), but groundTruthRelevantCount claims 1
        EvaluationCase inconsistentCase = new EvaluationCase(
                "TEST-UNIT-007", "MULTI_TERM", "Inconsistent", "query",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3, 7)), List.of(),
                "PUBLIC", "MATCH_FOUND", 1, "Inconsistent"
        );

        assertThrows(IllegalArgumentException.class, () ->
                validateTestCaseConsistency(inconsistentCase)
        );
    }

    @Test
    @DisplayName("Unit: Verify targetChunkIndices take precedence over section keywords without OR combining")
    void shouldPrioritizeTargetChunkIndicesOverKeywordsWithoutOrCombining() {
        // Target indices is strictly [3] (Batteries). Keywords mention 'Exclusions'.
        // Chunk 104 is chunkIndex 7 (Exclusions), which matches keywords but is NOT in targetChunkIndices.
        EvaluationCase tc = new EvaluationCase(
                "TEST-UNIT-008", "DIRECT_KEYWORD", "Query", "battery warranty",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(3)), List.of("General Warranty Exclusions"),
                "PUBLIC", "MATCH_FOUND", 1, "Precedence test"
        );

        UUID docId = UUID.randomUUID();
        RetrievedChunk nonTargetChunk = new RetrievedChunk(104L, docId, "POL-WAR-001", "Warranty Policy", 1, "3. General Warranty Exclusions", "Exclusion text", 100, 0.80);

        Map<Long, Integer> indexMap = Map.of(104L, 7);
        Map<Long, String> fixtureCodeMap = Map.of(104L, "POL-WAR-001");

        boolean relevant = isChunkSectionRelevant(nonTargetChunk, tc, indexMap, fixtureCodeMap, Map.of());
        assertFalse(relevant, "Chunk with non-matching index must NOT be relevant even if section title matches keyword");
    }

    @Test
    @DisplayName("Unit: Verify role-isolation detects unauthorized document leaked in positive query")
    void shouldDetectRoleIsolationViolationWhenUnauthorizedDocumentIsLeakedInPositiveQuery() {
        EvaluationCase publicCase = new EvaluationCase(
                "TEST-UNIT-009", "DIRECT_KEYWORD", "Warranty Query", "battery warranty",
                List.of("POL-WAR-001"), Map.of("POL-WAR-001", List.of(0)), List.of(),
                "PUBLIC", "MATCH_FOUND", 1, "Public user querying warranty"
        );

        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        RetrievedChunk authChunk = new RetrievedChunk(201L, docId1, "POL-WAR-001", "Warranty", 1, "Sec", "Text", 50, 0.95);
        RetrievedChunk leakedChunk = new RetrievedChunk(202L, docId2, "SOP-CSH-001", "Cashier SOP", 1, "Sec", "Text", 50, 0.85);

        Map<Long, Integer> indexMap = Map.of(201L, 0, 202L, 0);
        Map<Long, String> fixtureCodeMap = Map.of(201L, "POL-WAR-001", 202L, "SOP-CSH-001");
        Map<String, String> docRoleMap = Map.of("POL-WAR-001", "PUBLIC", "SOP-CSH-001", "ROLE_CASHIER");

        QueryEvaluationResult result = computeMetrics(
                publicCase, List.of(authChunk, leakedChunk), indexMap, fixtureCodeMap, Map.of(), docRoleMap, 1000L
        );

        assertFalse(result.roleIsolationMaintained(),
                "Role isolation must fail when a ROLE_CASHIER document is leaked to a PUBLIC user");
    }

    @Test
    @DisplayName("Unit: Verify role-isolation detects unrelated restricted document leaked alongside authorized ones")
    void shouldDetectRoleIsolationViolationWhenUnrelatedRestrictedDocumentIsLeaked() {
        EvaluationCase cashierCase = new EvaluationCase(
                "TEST-UNIT-010", "ROLE_ISOLATED", "Cash drop", "cash drop procedure",
                List.of("SOP-CSH-001"), Map.of("SOP-CSH-001", List.of(0)), List.of(),
                "ROLE_CASHIER", "MATCH_FOUND", 1, "Cashier user querying cashier SOP"
        );

        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        RetrievedChunk authChunk = new RetrievedChunk(301L, docId1, "SOP-CSH-001", "Cashier SOP", 1, "Sec", "Text", 50, 0.95);
        RetrievedChunk managerChunk = new RetrievedChunk(302L, docId2, "POL-MGR-CONF", "Manager Review", 1, "Sec", "Text", 50, 0.85);

        Map<Long, Integer> indexMap = Map.of(301L, 0, 302L, 0);
        Map<Long, String> fixtureCodeMap = Map.of(301L, "SOP-CSH-001", 302L, "POL-MGR-CONF");
        Map<String, String> docRoleMap = Map.of("SOP-CSH-001", "ROLE_CASHIER", "POL-MGR-CONF", "ROLE_MANAGER");

        // Subcase 1: ROLE_MANAGER document leaked to ROLE_CASHIER
        QueryEvaluationResult resultMgr = computeMetrics(
                cashierCase, List.of(authChunk, managerChunk), indexMap, fixtureCodeMap, Map.of(), docRoleMap, 1000L
        );
        assertFalse(resultMgr.roleIsolationMaintained(),
                "Role isolation must fail when a ROLE_MANAGER document is returned to a ROLE_CASHIER user");

        // Subcase 2: Unrecognized document defaults to fail-closed ROLE_ADMIN
        RetrievedChunk unknownChunk = new RetrievedChunk(303L, UUID.randomUUID(), "DOC-UNKNOWN", "Unknown", 1, "Sec", "Text", 50, 0.80);
        Map<Long, Integer> indexMap2 = Map.of(301L, 0, 303L, 0);
        Map<Long, String> fixtureCodeMap2 = Map.of(301L, "SOP-CSH-001", 303L, "DOC-UNKNOWN");

        QueryEvaluationResult resultUnknown = computeMetrics(
                cashierCase, List.of(authChunk, unknownChunk), indexMap2, fixtureCodeMap2, Map.of(), docRoleMap, 1000L
        );
        assertFalse(resultUnknown.roleIsolationMaintained(),
                "Role isolation must fail when an unrecognized document defaults to fail-closed ROLE_ADMIN");
    }

    @Test
    @DisplayName("Unit: Verify role-isolation is maintained when all retrieved documents are authorized")
    void shouldMaintainRoleIsolationWhenAllRetrievedDocumentsAreAuthorized() {
        EvaluationCase cashierCase = new EvaluationCase(
                "TEST-UNIT-011", "ROLE_ISOLATED", "Mixed query", "store policy",
                List.of("SOP-CSH-001"), Map.of("SOP-CSH-001", List.of(0)), List.of(),
                "ROLE_CASHIER", "MATCH_FOUND", 1, "Cashier user querying mixed policies"
        );

        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        RetrievedChunk publicChunk = new RetrievedChunk(401L, docId1, "POL-WAR-001", "Warranty", 1, "Sec", "Text", 50, 0.95);
        RetrievedChunk cashierChunk = new RetrievedChunk(402L, docId2, "SOP-CSH-001", "Cashier SOP", 1, "Sec", "Text", 50, 0.85);

        Map<Long, Integer> indexMap = Map.of(401L, 0, 402L, 0);
        Map<Long, String> fixtureCodeMap = Map.of(401L, "POL-WAR-001", 402L, "SOP-CSH-001");
        Map<String, String> docRoleMap = Map.of("POL-WAR-001", "PUBLIC", "SOP-CSH-001", "ROLE_CASHIER");

        QueryEvaluationResult result = computeMetrics(
                cashierCase, List.of(publicChunk, cashierChunk), indexMap, fixtureCodeMap, Map.of(), docRoleMap, 1000L
        );

        assertTrue(result.roleIsolationMaintained(),
                "Role isolation must be maintained when all retrieved documents (PUBLIC and ROLE_CASHIER) are accessible to ROLE_CASHIER");
    }

    // =========================================================================
    // Integration Tests: FTS Retrieval Evaluation Against PostgreSQL
    // =========================================================================
    @Test
    @DisplayName("1. Evaluate Exact Keyword Retrieval (Category A)")
    void shouldEvaluateExactKeywordRetrieval() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<EvaluationCase> dirCases = evaluationCases.stream()
                .filter(c -> "DIRECT_KEYWORD".equals(c.category()))
                .toList();
        assertFalse(dirCases.isEmpty(), "Evaluation dataset must contain DIRECT_KEYWORD cases");

        for (EvaluationCase tc : dirCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            assertTrue(res.documentHit(), "Direct keyword query must find expected document: " + tc.testCaseId());
            assertTrue(res.sectionHit(), "Direct keyword query must find relevant section: " + tc.testCaseId());
            assertTrue(res.reciprocalRank() >= 0.33, "Direct keyword query MRR must be >= 0.33 (top 3): " + tc.testCaseId());
        }
    }

    @Test
    @DisplayName("2. Evaluate Natural Language Retrieval with Multi-Tier Fallback (Category B)")
    void shouldEvaluateNaturalLanguageRetrieval() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<EvaluationCase> nlCases = evaluationCases.stream()
                .filter(c -> "NATURAL_LANGUAGE".equals(c.category()))
                .toList();
        assertFalse(nlCases.isEmpty(), "Evaluation dataset must contain NATURAL_LANGUAGE cases");

        int hits = 0;
        for (EvaluationCase tc : nlCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            if (res.documentHit()) {
                hits++;
            }
        }
        assertTrue(hits >= 2, "Multi-tier retrieval must preserve at least the Phase 3 natural-language document-hit floor");
    }

    @Test
    @DisplayName("3. Evaluate Term Variation and English Stemming (Category C)")
    void shouldEvaluateTermVariationAndStemming() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<EvaluationCase> varCases = evaluationCases.stream()
                .filter(c -> "TERM_VARIATION".equals(c.category()))
                .toList();
        assertFalse(varCases.isEmpty(), "Evaluation dataset must contain TERM_VARIATION cases");

        for (EvaluationCase tc : varCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            assertTrue(res.documentHit(), "Stemmed morphological variant query must match document: " + tc.testCaseId());
            assertTrue(res.sectionHit(), "Stemmed morphological variant query must match section: " + tc.testCaseId());
        }
    }

    @Test
    @DisplayName("4. Evaluate Multi-Term Queries (Category D)")
    void shouldEvaluateMultiTermQueries() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<EvaluationCase> mulCases = evaluationCases.stream()
                .filter(c -> "MULTI_TERM".equals(c.category()))
                .toList();
        assertFalse(mulCases.isEmpty(), "Evaluation dataset must contain MULTI_TERM cases");

        int hits = 0;
        for (EvaluationCase tc : mulCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            if (res.documentHit()) {
                hits++;
            }
        }
        assertTrue(hits >= 3, "Multi-term queries should find target document in >= 60% of cases");
    }

    private static String describeRetrievedChunks(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "[]";
        }
        return chunks.stream()
                .map(chunk -> {
                    String fixtureCode = generatedToFixtureCodeMap.getOrDefault(
                            chunk.documentCode(), chunk.documentCode());
                    String requiredRole = docCodeToRequiredRoleMap.getOrDefault(fixtureCode, "UNKNOWN_FAIL_CLOSED");
                    return "{chunkId=" + chunk.chunkId()
                            + ", documentCode=" + chunk.documentCode()
                            + ", fixtureCode=" + fixtureCode
                            + ", requiredRole=" + requiredRole
                            + ", title=" + chunk.documentTitle()
                            + ", section=" + chunk.sectionTitle() + "}";
                })
                .toList().toString();
    }

    @Test
    @DisplayName("5. Evaluate Negative and Out-of-Scope Queries (Category E)")
    void shouldEvaluateNegativeAndOutOfScopeQueries() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<EvaluationCase> negCases = evaluationCases.stream()
                .filter(c -> "NEGATIVE_OUT_OF_SCOPE".equals(c.category()))
                .toList();
        assertFalse(negCases.isEmpty(), "Evaluation dataset must contain NEGATIVE_OUT_OF_SCOPE cases");

        for (EvaluationCase tc : negCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            String returned = describeRetrievedChunks(res.retrievedTop5());
            assertTrue(res.isNegativeCorrect(), "Negative out-of-scope query must produce 0 retrieved chunks: "
                    + tc.testCaseId() + "; returned=" + returned);
            assertEquals(0, res.retrievedTop5().size(), "Out-of-scope query must not retrieve false-positive chunks: "
                    + tc.testCaseId() + "; returned=" + returned);
        }
    }

    @Test
    @DisplayName("6. Evaluate Server-Side Role-Based Retrieval Isolation (Category F)")
    void shouldEvaluateRoleBasedRetrievalIsolation() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<EvaluationCase> rolCases = evaluationCases.stream()
                .filter(c -> "ROLE_RESTRICTED".equals(c.category()))
                .toList();
        assertFalse(rolCases.isEmpty(), "Evaluation dataset must contain ROLE_RESTRICTED cases");

        for (EvaluationCase tc : rolCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            assertTrue(res.roleIsolationMaintained(), "Role isolation must be strictly maintained for " + tc.testCaseId());
            if ("NO_MATCH".equals(tc.expectedOutcome())) {
                assertEquals(0, res.retrievedTop5().size(),
                        "Role-blocked query must return no chunks: " + tc.testCaseId()
                                + "; userRole=" + tc.userRole()
                                + "; returned=" + describeRetrievedChunks(res.retrievedTop5()));
            } else {
                assertFalse(res.retrievedTop5().isEmpty(),
                        "Authorized role must retrieve document: " + tc.testCaseId());
            }
        }
    }

    @Test
    @DisplayName("6b. Verify explicit two-sided role authorization on confirmed cashier terms (ROLE_CASHIER vs PUBLIC)")
    void shouldVerifyRoleBasedAuthorizationForConfirmedCashierTerms() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        String query = "opening cash float amount";
        String sopGeneratedCode = fixtureToGeneratedCodeMap.get("SOP-CSH-001");
        assertNotNull(sopGeneratedCode, "SOP generated document code must exist in mapping");

        // 1. Authorized execution as ROLE_CASHIER: must retrieve SOP-CSH-001
        Set<String> cashierRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("ROLE_CASHIER"));
        List<RetrievedChunk> cashierResults = engine.retrieve(query, cashierRoles, 5);
        assertFalse(cashierResults.isEmpty(), "Authorized ROLE_CASHIER user must retrieve cashier SOP");
        boolean cashierFoundSop = cashierResults.stream()
                .anyMatch(c -> sopGeneratedCode.equals(c.documentCode()));
        assertTrue(cashierFoundSop, "Retrieved results for ROLE_CASHIER must include SOP-CSH-001");

        // Verify that the retrieved SOP chunk has chunk_index = 0
        boolean cashierFoundExpectedChunk0 = cashierResults.stream()
                .filter(c -> sopGeneratedCode.equals(c.documentCode()))
                .anyMatch(c -> Integer.valueOf(0).equals(chunkIdToIndexMap.get(c.chunkId())));
        assertTrue(cashierFoundExpectedChunk0,
                "Authorized ROLE_CASHIER retrieval must return expected Section 1 opening cash float chunk (chunk_index = 0)");

        // 2. Unauthorized execution as PUBLIC: must NOT retrieve SOP-CSH-001
        Set<String> publicRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("PUBLIC"));
        List<RetrievedChunk> publicResults = engine.retrieve(query, publicRoles, 5);
        boolean publicLeakedSop = publicResults.stream()
                .anyMatch(c -> sopGeneratedCode.equals(c.documentCode()));
        assertFalse(publicLeakedSop, "PUBLIC user must be strictly blocked from retrieving cashier SOP at SQL boundary");

        // 3. Authorized execution as ROLE_MANAGER (role inheritance): must also retrieve SOP-CSH-001 chunk 0
        Set<String> managerRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("ROLE_MANAGER"));
        List<RetrievedChunk> managerResults = engine.retrieve(query, managerRoles, 5);
        assertFalse(managerResults.isEmpty(), "Authorized ROLE_MANAGER user must inherit access to cashier SOP");
        boolean managerFoundExpectedChunk0 = managerResults.stream()
                .filter(c -> sopGeneratedCode.equals(c.documentCode()))
                .anyMatch(c -> Integer.valueOf(0).equals(chunkIdToIndexMap.get(c.chunkId())));
        assertTrue(managerFoundExpectedChunk0,
                "Authorized ROLE_MANAGER retrieval must inherit access to Section 1 opening cash float chunk (chunk_index = 0)");
    }

    @Test
    @DisplayName("7. Inactive documents must be strictly excluded from FTS retrieval")
    void shouldExcludeInactiveDocuments() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        UUID inactiveDocId = UUID.randomUUID();
        String inactiveDocCode = "INACT_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Inactive Test Document', 'POLICY', 'TEXT', 'PUBLIC', 1, FALSE, 'inactive.txt', 'hash_inact', 100, '{}'::jsonb, 'TEST')
                    """,
                    inactiveDocId, inactiveDocCode
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Exclusive Keyword Section', 'HyperSpecificExclusiveKeywordXYZ battery warranty return procedure.', 15, 'hash_c_inact', TRUE)
                    """,
                    inactiveDocId
            );

            List<RetrievedChunk> results = engine.retrieve("HyperSpecificExclusiveKeywordXYZ", Set.of("PUBLIC"), 5);
            assertTrue(results.isEmpty(), "Inactive document chunks must never be returned by retrieval engine");
        } finally {
            cleanUpDocIds(List.of(inactiveDocId));
        }
    }

    @Test
    @DisplayName("8. Inactive chunks of active documents must be excluded while active chunks are retrieved")
    void shouldExcludeInactiveChunks() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        UUID docId = UUID.randomUUID();
        String docCode = "DEACT_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Partially Deactivated Document', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'part.txt', 'hash_part', 100, '{}'::jsonb, 'TEST')
                    """,
                    docId, docCode
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Active Section', 'ActiveUniqueTermABC ignition sensor calibration.', 10, 'hash_c_act', TRUE)
                    """,
                    docId
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 1, 'Inactive Section', 'InactiveUniqueTermDEF ignition sensor calibration.', 10, 'hash_c_deact', FALSE)
                    """,
                    docId
            );

            List<RetrievedChunk> actResults = engine.retrieve("ActiveUniqueTermABC", Set.of("PUBLIC"), 5);
            assertEquals(1, actResults.size(), "Active chunk must be retrieved");
            assertEquals("Active Section", actResults.getFirst().sectionTitle());

            List<RetrievedChunk> inactResults = engine.retrieve("InactiveUniqueTermDEF", Set.of("PUBLIC"), 5);
            assertTrue(inactResults.isEmpty(), "Inactive chunk must not be retrieved even if document is active");
        } finally {
            cleanUpDocIds(List.of(docId));
        }
    }

    @Test
    @DisplayName("9. Active document version behavior: superseded version chunks are excluded")
    void shouldRespectDocumentVersionTransitions() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        String versionDocCode = "VERS_" + UUID.randomUUID().toString().substring(0, 8);
        UUID v1Id = UUID.randomUUID();
        UUID v2Id = UUID.randomUUID();

        try {
            // Version 1 (active = FALSE)
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Warranty Versioned Policy', 'POLICY', 'TEXT', 'PUBLIC', 1, FALSE, 'v1.txt', 'h1', 100, '{}'::jsonb, 'TEST')
                    """,
                    v1Id, versionDocCode
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Version 1 Rule', 'OldVersionRuleTermXYZ allows 14 days warranty replacement.', 15, 'ch1', FALSE)
                    """,
                    v1Id
            );

            // Version 2 (active = TRUE)
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Warranty Versioned Policy', 'POLICY', 'TEXT', 'PUBLIC', 2, TRUE, 'v2.txt', 'h2', 100, '{}'::jsonb, 'TEST')
                    """,
                    v2Id, versionDocCode
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Version 2 Rule', 'NewVersionRuleTermXYZ allows 30 days warranty replacement.', 15, 'ch2', TRUE)
                    """,
                    v2Id
            );

            List<RetrievedChunk> oldResults = engine.retrieve("OldVersionRuleTermXYZ", Set.of("PUBLIC"), 5);
            assertTrue(oldResults.isEmpty(), "Superseded inactive version chunks must not be returned");

            List<RetrievedChunk> newResults = engine.retrieve("NewVersionRuleTermXYZ", Set.of("PUBLIC"), 5);
            assertEquals(1, newResults.size(), "Active version chunk must be retrieved");
            assertEquals(2, newResults.getFirst().version(), "Retrieved version must be 2");
        } finally {
            cleanUpDocIds(List.of(v1Id, v2Id));
        }
    }

    @Test
    @DisplayName("10. Verify Top-K ranking honors limit parameter and ranks denser matches higher")
    void shouldVerifyTopKRankingAndNormalizationFlag5() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<RetrievedChunk> top1 = engine.retrieve("warranty", Set.of("PUBLIC"), 1);
        assertEquals(1, top1.size(), "Limit 1 must return exactly 1 result");

        List<RetrievedChunk> top3 = engine.retrieve("warranty", Set.of("PUBLIC"), 3);
        assertEquals(3, top3.size(), "Limit 3 must return exactly 3 results");

        List<RetrievedChunk> top5 = engine.retrieve("warranty", Set.of("PUBLIC"), 5);
        assertTrue(top5.size() >= 3, "Limit 5 must return up to 5 results");

        // Verify descending relevance score ordering
        for (int i = 0; i < top5.size() - 1; i++) {
            assertTrue(top5.get(i).relevanceScore() >= top5.get(i + 1).relevanceScore(),
                    "Results must be sorted in descending order of relevance score");
        }
    }

    @Test
    @DisplayName("11. Demonstrate stable repeatability across repeated executions")
    void shouldDemonstrateStableRepeatabilityOfResults() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        String query = "battery warranty period replacement";
        List<RetrievedChunk> run1 = engine.retrieve(query, Set.of("PUBLIC"), 4);
        List<RetrievedChunk> run2 = engine.retrieve(query, Set.of("PUBLIC"), 4);
        List<RetrievedChunk> run3 = engine.retrieve(query, Set.of("PUBLIC"), 4);

        assertEquals(run1.size(), run2.size());
        assertEquals(run2.size(), run3.size());

        for (int i = 0; i < run1.size(); i++) {
            assertEquals(run1.get(i).chunkId(), run2.get(i).chunkId());
            assertEquals(run2.get(i).chunkId(), run3.get(i).chunkId());
            assertEquals(run1.get(i).relevanceScore(), run2.get(i).relevanceScore(), 0.0001);
            assertEquals(run2.get(i).relevanceScore(), run3.get(i).relevanceScore(), 0.0001);
        }
    }

    @Test
    @DisplayName("12. Verify deterministic tie-breaking across repeated retrieval executions")
    void shouldVerifyDeterministicTieBreakerBehavior() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        List<Long> firstRun = engine.retrieve("warranty", Set.of("PUBLIC"), 10)
                .stream().map(RetrievedChunk::chunkId).toList();
        List<Long> secondRun = engine.retrieve("warranty", Set.of("PUBLIC"), 10)
                .stream().map(RetrievedChunk::chunkId).toList();
        List<Long> thirdRun = engine.retrieve("warranty", Set.of("PUBLIC"), 10)
                .stream().map(RetrievedChunk::chunkId).toList();

        assertFalse(firstRun.isEmpty(), "Expected multiple results to inspect deterministic ordering");
        assertEquals(firstRun, secondRun, "Chunk ordering must be stable on the second execution");
        assertEquals(secondRun, thirdRun, "Chunk ordering must be stable on the third execution");
        assertEquals(firstRun.size(), firstRun.stream().distinct().count(),
                "Merged retrieval must not return duplicate chunk IDs");
    }

    @Test
    @DisplayName("13. Execute full 28-query evaluation suite and compute Precision@K, Recall@K, MRR, and Latency")
    void shouldComputeAndPrintComprehensiveEvaluationReport() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        assertFalse(evaluationCases.isEmpty(), "Evaluation dataset must not be empty");
        List<QueryEvaluationResult> results = new ArrayList<>();

        System.out.println("========================================================================================================================");
        System.out.println("STAGE 3C PHASE 4: OPTIMIZED MULTI-TIER RETRIEVAL BENCHMARK REPORT");
        System.out.println("Engine: PostgreSQL FTS (phrase -> AND -> weighted OR; deterministic merge; ts_rank_cd flag 5)");
        System.out.println("Corpus: 4 Documents, 33 Chunks, PostgreSQL 18.4 | Run ID: " + RUN_ID);
        System.out.println("========================================================================================================================");
        System.out.printf("%-12s | %-16s | %-6s | %-6s | %-4s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-6s%n",
                "Case ID", "Category", "DocHit", "SecHit", "Rank", "P@1", "P@3", "P@5", "R@1", "R@3", "R@5", "MRR", "Latency");
        System.out.println("-------------+------------------+--------+--------+------+-------+-------+-------+-------+-------+-------+-------+-------");

        for (EvaluationCase tc : evaluationCases) {
            QueryEvaluationResult res = evaluateQuery(tc);
            results.add(res);
            boolean isNegOrBlocked = tc.expectedDocumentCodes().isEmpty() || "NO_MATCH".equals(tc.expectedOutcome());
            System.out.printf("%-12s | %-16s | %-6s | %-6s | %-4s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %4.1fms%n",
                    tc.testCaseId(),
                    tc.category(),
                    res.documentHit() ? "YES" : (isNegOrBlocked ? "N/A" : "NO"),
                    res.sectionHit() ? "YES" : (isNegOrBlocked ? "N/A" : "NO"),
                    res.firstRelevantRank() > 0 ? String.valueOf(res.firstRelevantRank()) : "-",
                    formatMetric(res.precisionAt1Fixed()),
                    formatMetric(res.precisionAt3Fixed()),
                    formatMetric(res.precisionAt5Fixed()),
                    formatMetric(res.recallAt1()),
                    formatMetric(res.recallAt3()),
                    formatMetric(res.recallAt5()),
                    formatMetric(res.reciprocalRank()),
                    res.latencyNanos() / 1_000_000.0
            );
        }

        // Aggregate statistics across standard positive queries (Categories A, B, C, D, and authorized F)
        List<QueryEvaluationResult> positiveQueries = results.stream()
                .filter(r -> !"NEGATIVE_OUT_OF_SCOPE".equals(r.testCase().category())
                        && !"NO_MATCH".equals(r.testCase().expectedOutcome()))
                .toList();

        long docHits = positiveQueries.stream().filter(QueryEvaluationResult::documentHit).count();
        long secHits = positiveQueries.stream().filter(QueryEvaluationResult::sectionHit).count();
        double docHitRate = (double) docHits / positiveQueries.size() * 100.0;
        double secHitRate = (double) secHits / positiveQueries.size() * 100.0;

        DoubleSummaryStatistics mrrStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::reciprocalRank).summaryStatistics();
        DoubleSummaryStatistics p1FixedStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::precisionAt1Fixed).summaryStatistics();
        DoubleSummaryStatistics p3FixedStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::precisionAt3Fixed).summaryStatistics();
        DoubleSummaryStatistics p5FixedStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::precisionAt5Fixed).summaryStatistics();
        DoubleSummaryStatistics p1AdaptiveStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::precisionAt1Adaptive).summaryStatistics();
        DoubleSummaryStatistics p3AdaptiveStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::precisionAt3Adaptive).summaryStatistics();
        DoubleSummaryStatistics p5AdaptiveStats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::precisionAt5Adaptive).summaryStatistics();
        DoubleSummaryStatistics r1Stats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::recallAt1).summaryStatistics();
        DoubleSummaryStatistics r3Stats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::recallAt3).summaryStatistics();
        DoubleSummaryStatistics r5Stats = positiveQueries.stream().mapToDouble(QueryEvaluationResult::recallAt5).summaryStatistics();

        // Negative query statistics (Category E + role blocked)
        List<QueryEvaluationResult> negativeQueries = results.stream()
                .filter(r -> "NEGATIVE_OUT_OF_SCOPE".equals(r.testCase().category())
                        || "NO_MATCH".equals(r.testCase().expectedOutcome()))
                .toList();
        long trueNegatives = negativeQueries.stream().filter(QueryEvaluationResult::isNegativeCorrect).count();
        double trueNegativeRate = (double) trueNegatives / negativeQueries.size() * 100.0;

        // Role isolation
        long roleViolations = results.stream().filter(r -> !r.roleIsolationMaintained()).count();

        // Latency
        LongSummaryStatistics latencyStats = results.stream().mapToLong(QueryEvaluationResult::latencyNanos).summaryStatistics();

        System.out.println("========================================================================================================================");
        System.out.println("AGGREGATE RETRIEVAL METRICS (PHASE 4 OPTIMIZED EVALUATION)");
        System.out.println("========================================================================================================================");
        System.out.printf("Total Evaluation Queries: %d (Positive: %d, Negative/Blocked: %d)%n",
                results.size(), positiveQueries.size(), negativeQueries.size());
        System.out.printf("Document Hit Rate: %.1f%% (%d / %d)%n", docHitRate, docHits, positiveQueries.size());
        System.out.printf("Section Hit Rate:  %.1f%% (%d / %d)%n", secHitRate, secHits, positiveQueries.size());
        System.out.printf("Mean Reciprocal Rank (MRR): %.4f (across positive queries)%n", mrrStats.getAverage());
        System.out.printf("Precision@K (Fixed Denom: rel/K):      P@1: %.4f | P@3: %.4f | P@5: %.4f%n",
                p1FixedStats.getAverage(), p3FixedStats.getAverage(), p5FixedStats.getAverage());
        System.out.printf("Precision@K (Adaptive: rel/min(K,ret)): P@1: %.4f | P@3: %.4f | P@5: %.4f%n",
                p1AdaptiveStats.getAverage(), p3AdaptiveStats.getAverage(), p5AdaptiveStats.getAverage());
        System.out.printf("Recall@K (rel in top-K / ground truth): R@1: %.4f | R@3: %.4f | R@5: %.4f%n",
                r1Stats.getAverage(), r3Stats.getAverage(), r5Stats.getAverage());
        System.out.printf("True Negative / Zero False-Positive Rate: %.1f%% (%d / %d)%n",
                trueNegativeRate, trueNegatives, negativeQueries.size());
        System.out.printf("Role-Based Isolation Violations: %d%n", roleViolations);
        System.out.printf("Latency (ms) -> Avg: %.2f ms, Min: %.2f ms, Max: %.2f ms%n",
                latencyStats.getAverage() / 1_000_000.0,
                latencyStats.getMin() / 1_000_000.0,
                latencyStats.getMax() / 1_000_000.0);
        printPhase3BaselineComparison(
                docHitRate,
                secHitRate,
                mrrStats.getAverage(),
                p3AdaptiveStats.getAverage(),
                r5Stats.getAverage(),
                trueNegativeRate,
                roleViolations,
                latencyStats.getAverage() / 1_000_000.0
        );
        System.out.println("========================================================================================================================");

        // Core assertions proving baseline guarantees:
        // Zero role violations and 100% negative-query zero-result rate are strict guarantees.
        // Baseline retrieval metrics (DocHit, SecHit, MRR) are measured and printed without arbitrary thresholds.
        assertEquals(0, roleViolations, "Role-based retrieval isolation violations must be strictly 0");
        assertEquals(100.0, trueNegativeRate, "Negative/blocked queries must achieve 100% true negative rate (0 false positives)");
    }

    private static void printPhase3BaselineComparison(
            double documentHitRate,
            double sectionHitRate,
            double mrr,
            double adaptivePrecisionAt3,
            double recallAt5,
            double trueNegativeRate,
            long roleViolations,
            double averageLatencyMs
    ) {
        System.out.println("---------------- PHASE 3 BASELINE COMPARISON ----------------");
        printDelta("Document Hit Rate", documentHitRate, 78.3, "percentage points");
        printDelta("Section Hit Rate", sectionHitRate, 69.6, "percentage points");
        printDelta("MRR", mrr, 0.6232, "absolute");
        printDelta("Adaptive Precision@3", adaptivePrecisionAt3, 0.5507, "absolute");
        printDelta("Recall@5", recallAt5, 0.6377, "absolute");
        printDelta("Negative zero-result rate", trueNegativeRate, 100.0, "percentage points");
        System.out.printf(Locale.ROOT, "Role isolation violations: %d (Phase 3 baseline: 0)%n", roleViolations);
        printDelta("Average latency (ms)", averageLatencyMs, 0.81, "ms");
        System.out.println("Dataset: unchanged 28-query Phase 3 evaluation corpus (SHA-256 verified)");
        System.out.println("--------------------------------------------------------------");
    }

    private static void printDelta(String metric, double actual, double baseline, String unit) {
        System.out.printf(Locale.ROOT, "%s: %.4f | Phase 3: %.4f | Delta: %+.4f %s%n",
                metric, actual, baseline, actual - baseline, unit);
    }

    private static String formatMetric(double val) {
        return Double.isNaN(val) ? "N/A" : String.format(Locale.ROOT, "%.2f", val);
    }
}
