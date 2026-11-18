package com.autoparts.erp.ai.rag.retrieval;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live integration test for PostgreSQL native full-text search (FTS) lexical retrieval.
 *
 * <p>Validates PostgreSQL 18 native FTS constructs including {@code websearch_to_tsquery('english', ...)},
 * {@code ts_rank_cd(..., 5)}, GIN index matching, generated {@code tsvector} column behavior,
 * double-active filtering, SQL role filtering, and partial unique index constraints against a live database.
 * If live PostgreSQL is not accessible on the configured host and port, tests are skipped gracefully.</p>
 */
class PostgresFtsRetrievalIntegrationTest {

    private static String dbUrl = System.getProperty("DB_URL", System.getenv("DB_URL"));
    private static String dbUser = System.getProperty("DB_USERNAME", System.getenv("DB_USERNAME"));
    private static String dbPass = System.getProperty("DB_PASSWORD", System.getenv("DB_PASSWORD"));

    private static final boolean MIGRATION_OPT_IN = Boolean.parseBoolean(
            System.getProperty("test.db.migrate", System.getenv().getOrDefault("TEST_DB_MIGRATE", "false"))
    );

    private static boolean postgresAvailable = false;

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
            throw new IllegalStateException(
                    "Refusing to execute integration test against production database target: " + url
            );
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
            throw new IllegalStateException(
                    "Refusing to execute integration test against non-local database target without explicit override (-Dtest.allow.remote.db=true): " + url
            );
        }
        String activeProfile = System.getProperty("spring.profiles.active", System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));
        if (activeProfile.toLowerCase().contains("prod")) {
            throw new IllegalStateException(
                    "Refusing to execute integration test with production profile active: " + activeProfile
            );
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

    static Flyway createFlywayMigrationConfig(String url, String user, String pass) {
        return Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .target("32")
                .load();
    }

    @BeforeAll
    static void checkPostgresAvailabilityAndSetup() {
        resolveCredentials();
        validateSafeDatabaseTarget(dbUrl);

        // 1. Probe network reachability of PostgreSQL host and port parsed from dbUrl.
        // Genuinely unavailable service skips the live tests gracefully.
        HostPort hostPort = parseHostAndPort(dbUrl);
        if (!isServiceReachable(hostPort.host(), hostPort.port(), 1000)) {
            postgresAvailable = false;
            return;
        }

        // 2. Service is reachable. Any failure to authenticate, migrate, or query
        // must fail fast and NOT be masked as service unavailability.
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            if (MIGRATION_OPT_IN) {
                Flyway flyway = createFlywayMigrationConfig(dbUrl, dbUser, dbPass);
                flyway.migrate();
            }

            SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, false);
            NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(ds);
            Integer tableCount = template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'knowledge_documents'",
                    Integer.class
            );
            if (tableCount == null || tableCount == 0) {
                if (MIGRATION_OPT_IN) {
                    throw new IllegalStateException("Flyway migration to target V32 succeeded but knowledge_documents was not created");
                } else {
                    // Service is running but V32 has not been applied and migration was not opted in
                    postgresAvailable = false;
                    return;
                }
            }
            postgresAvailable = true;
        } catch (SQLException e) {
            postgresAvailable = false;
            throw new RuntimeException("PostgreSQL service is reachable, but connection/authentication failed with database: " + e.getMessage(), e);
        } catch (FlywayException e) {
            postgresAvailable = false;
            throw new RuntimeException("PostgreSQL Flyway migration failed: " + e.getMessage(), e);
        } catch (DataAccessException e) {
            postgresAvailable = false;
            throw new RuntimeException("PostgreSQL schema query failed: " + e.getMessage(), e);
        } catch (Exception e) {
            postgresAvailable = false;
            throw new RuntimeException("Unexpected error during PostgreSQL test setup: " + e.getMessage(), e);
        }
    }

    private SingleConnectionDataSource createDataSource() {
        return new SingleConnectionDataSource(dbUrl, dbUser, dbPass, false);
    }

    @Test
    @DisplayName("Test safety guard rejects production database targets and unauthorized remote hosts")
    void shouldRejectProductionDatabaseTargets() {
        assertThrows(IllegalStateException.class, () ->
                validateSafeDatabaseTarget("jdbc:postgresql://prod-db.internal:5432/autoparts_erp")
        );
        assertThrows(IllegalStateException.class, () ->
                validateSafeDatabaseTarget("jdbc:postgresql://production-cluster.rds.amazonaws.com:5432/autoparts_erp")
        );
        assertThrows(IllegalStateException.class, () ->
                validateSafeDatabaseTarget("jdbc:postgresql://remote-host.com:5432/autoparts_erp")
        );
        assertDoesNotThrow(() ->
                validateSafeDatabaseTarget("jdbc:postgresql://localhost:5432/autoparts_erp")
        );
        assertDoesNotThrow(() ->
                validateSafeDatabaseTarget("jdbc:postgresql://127.0.0.1:5432/autoparts_erp")
        );
    }

    @Test
    @DisplayName("Flyway configuration must restrict migration target strictly to V32")
    void shouldConfigureFlywayTargetRestrictedToV32() {
        Flyway flyway = createFlywayMigrationConfig(dbUrl, dbUser, dbPass);
        assertNotNull(flyway.getConfiguration().getTarget());
        assertEquals("32", flyway.getConfiguration().getTarget().getVersion());
        assertTrue(flyway.getConfiguration().isBaselineOnMigrate());
    }

    @Test
    @DisplayName("Parse host and port correctly from PostgreSQL JDBC URLs across formats")
    void shouldParseHostAndPortFromPostgreSqlJdbcUrl() {
        assertEquals(new HostPort("localhost", 5432), parseHostAndPort("jdbc:postgresql://localhost:5432/autoparts_erp"));
        assertEquals(new HostPort("127.0.0.1", 5432), parseHostAndPort("jdbc:postgresql://127.0.0.1:5432/autoparts_erp"));
        assertEquals(new HostPort("127.0.0.1", 5432), parseHostAndPort("jdbc:postgresql://127.0.0.1/autoparts_erp"));
        assertEquals(new HostPort("remote-db.corp.internal", 5433), parseHostAndPort("jdbc:postgresql://remote-db.corp.internal:5433/autoparts_erp?sslmode=require"));
        assertEquals(new HostPort("remote-db.corp.internal", 5432), parseHostAndPort("jdbc:postgresql://remote-db.corp.internal/autoparts_erp"));
        assertEquals(new HostPort("::1", 5432), parseHostAndPort("jdbc:postgresql://[::1]:5432/autoparts_erp"));
        assertEquals(new HostPort("remote-db.internal", 5432), parseHostAndPort("jdbc:postgresql://testuser:secret@remote-db.internal:5432/autoparts_erp"));
        assertEquals(new HostPort("localhost", 5432), parseHostAndPort(""));
        assertEquals(new HostPort("localhost", 5432), parseHostAndPort(null));
    }

    @Test
    @DisplayName("Safety guard permits localhost, 127.0.0.1, and remote hosts with explicit opt-in while strictly enforcing production guard")
    void shouldAllowLocalhostAnd127001AndRemoteHostWithExplicitOptIn() {
        // Localhost and 127.0.0.1 permitted by default without opt-in
        assertDoesNotThrow(() ->
                validateSafeDatabaseTarget("jdbc:postgresql://localhost:5432/autoparts_erp")
        );
        assertDoesNotThrow(() ->
                validateSafeDatabaseTarget("jdbc:postgresql://127.0.0.1:5432/autoparts_erp")
        );

        // Remote host rejected by default without explicit opt-in
        assertThrows(IllegalStateException.class, () ->
                validateSafeDatabaseTarget("jdbc:postgresql://remote-staging.internal:5432/autoparts_erp")
        );

        // Remote host permitted with explicit opt-in
        String prevOptIn = System.getProperty("test.allow.remote.db");
        try {
            System.setProperty("test.allow.remote.db", "true");
            assertDoesNotThrow(() ->
                    validateSafeDatabaseTarget("jdbc:postgresql://remote-staging.internal:5432/autoparts_erp")
            );
            assertDoesNotThrow(() ->
                    validateSafeDatabaseTarget("jdbc:postgresql://remote-staging.internal:5433/autoparts_erp")
            );

            // Production guard is NEVER weakened by remote opt-in
            assertThrows(IllegalStateException.class, () ->
                    validateSafeDatabaseTarget("jdbc:postgresql://prod-db.internal:5432/autoparts_erp")
            );
            assertThrows(IllegalStateException.class, () ->
                    validateSafeDatabaseTarget("jdbc:postgresql://production-cluster.rds.amazonaws.com:5432/autoparts_erp")
            );
            assertThrows(IllegalStateException.class, () ->
                    validateSafeDatabaseTarget("jdbc:postgresql://live-db.corp:5432/autoparts_erp")
            );
        } finally {
            if (prevOptIn != null) {
                System.setProperty("test.allow.remote.db", prevOptIn);
            } else {
                System.clearProperty("test.allow.remote.db");
            }
        }
    }

    @Test
    @DisplayName("Probing an unallocated local port must report unreachable gracefully without throwing an unhandled exception")
    void shouldDetectUnreachableServicePortGracefully() {
        HostPort localhostHp = parseHostAndPort("jdbc:postgresql://localhost:59998/autoparts_erp");
        boolean reachable = isServiceReachable(localhostHp.host(), localhostHp.port(), 200);
        assertFalse(reachable, "Unallocated port 59998 on parsed localhost should report unreachable");

        HostPort ipHp = parseHostAndPort("jdbc:postgresql://127.0.0.1:59998/autoparts_erp");
        boolean ipReachable = isServiceReachable(ipHp.host(), ipHp.port(), 200);
        assertFalse(ipReachable, "Unallocated port 59998 on parsed 127.0.0.1 should report unreachable");
    }

    @Test
    @DisplayName("Authentication failure must fail fast with SQLException and not be masked as service unavailability")
    void shouldFailFastOnAuthenticationFailureWhenServiceIsReachable() {
        HostPort hostPort = parseHostAndPort(dbUrl);
        Assumptions.assumeTrue(isServiceReachable(hostPort.host(), hostPort.port(), 1000), "PostgreSQL port must be open to test auth failure");
        assertThrows(SQLException.class, () ->
                DriverManager.getConnection(dbUrl, dbUser, "wrong_password_xyz_test")
        );
    }

    @Test
    @DisplayName("Verify Flyway V32 migration applied and catalog artifacts (tables, GIN index, partial unique index) exist")
    void shouldVerifyV32MigrationAndCatalogArtifacts() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        try {
            NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);

            // 1. Verify tables exist in information_schema
            Integer docTableCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'knowledge_documents'",
                    Integer.class
            );
            assertEquals(1, docTableCount, "knowledge_documents table must exist in public schema");

            Integer chunkTableCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'knowledge_chunks'",
                    Integer.class
            );
            assertEquals(1, chunkTableCount, "knowledge_chunks table must exist in public schema");

            // 2. Verify GIN index exists
            Integer ginIndexCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'knowledge_chunks' AND indexname = 'idx_knowledge_chunks_tsv'",
                    Integer.class
            );
            assertEquals(1, ginIndexCount, "GIN index idx_knowledge_chunks_tsv must exist");

            // 3. Verify partial unique index exists
            Integer uniqueIndexCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'knowledge_documents' AND indexname = 'uq_knowledge_documents_single_active_version'",
                    Integer.class
            );
            assertEquals(1, uniqueIndexCount, "Partial unique index uq_knowledge_documents_single_active_version must exist");

        } finally {
            ds.destroy();
        }
    }

    @Test
    @DisplayName("Verify generated tsvector column chunk_vector is populated automatically upon chunk insertion")
    void shouldVerifyGeneratedTsvectorColumnBehavior() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        UUID docId = UUID.randomUUID();
        String docCode = "TEST-TSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'TSVector Test Doc', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'tsv.txt', 'hash_tsv', 100, '{}'::jsonb, 'TEST')
                    """,
                    docId, docCode
            );

            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Return Guidelines', 'Brake rotors may be returned within 30 days if unused in original packaging.', 20, 'hash_chunk_tsv', TRUE)
                    """,
                    docId
            );

            // Query chunk_vector
            String tsvectorStr = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT chunk_vector::text FROM knowledge_chunks WHERE document_id = ? AND chunk_index = 0",
                    String.class,
                    docId
            );

            assertNotNull(tsvectorStr, "chunk_vector must not be null");
            assertTrue(tsvectorStr.contains("brake"), "chunk_vector must contain stemmed 'brake'");
            assertTrue(tsvectorStr.contains("rotor"), "chunk_vector must contain stemmed 'rotor'");
            assertTrue(tsvectorStr.contains("return"), "chunk_vector must contain stemmed 'return'");
            assertTrue(tsvectorStr.contains("guidelin"), "chunk_vector must contain stemmed 'guidelin' from section title");

        } finally {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id = ?", docId);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id = ?", docId);
            } finally {
                ds.destroy();
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL FTS retrieval executes native websearch_to_tsquery and ts_rank_cd against live database")
    void shouldExecuteWebsearchToTsqueryAndRankCd() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        PostgresFtsRetrievalEngine engine = new PostgresFtsRetrievalEngine(jdbcTemplate);

        UUID docId = UUID.randomUUID();
        String docCode = "TEST-FTS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Integration Test Document', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'test.txt', 'hash_test_123', 100, '{}'::jsonb, 'TEST')
                    """,
                    docId, docCode
            );

            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Warranty Section', 'All OEM alternators and brake calipers carry a 24-month replacement warranty.', 15, 'chunk_hash_1', TRUE)
                    """,
                    docId
            );

            List<RetrievedChunk> results = engine.retrieve("alternators warranty", Set.of("PUBLIC"), 5);

            assertNotNull(results);
            assertFalse(results.isEmpty(), "Expected FTS match on 'alternators warranty'");
            RetrievedChunk matched = results.stream()
                    .filter(c -> docCode.equals(c.documentCode()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(matched, "Expected inserted test chunk to be returned by FTS query");
            assertTrue(matched.relevanceScore() > 0.0, "Relevance score should be greater than 0");
            assertEquals("Warranty Section", matched.sectionTitle());
            assertEquals("Integration Test Document", matched.documentTitle());
            assertEquals(1, matched.version());

        } finally {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id = ?", docId);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id = ?", docId);
            } finally {
                ds.destroy();
            }
        }
    }

    @Test
    @DisplayName("Verify ts_rank_cd flag 5 normalization orders compact dense matches ahead of sparse matches")
    void shouldOrderResultsByTsRankCdFlag5() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        PostgresFtsRetrievalEngine engine = new PostgresFtsRetrievalEngine(jdbcTemplate);

        UUID docIdDense = UUID.randomUUID();
        UUID docIdSparse = UUID.randomUUID();
        String codeDense = "TEST-DENSE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String codeSparse = "TEST-SPARSE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            // Doc 1: Dense match - short text, search terms right next to each other
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Dense Match Doc', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'dense.txt', 'hash_dense', 100, '{}'::jsonb, 'TEST')
                    """,
                    docIdDense, codeDense
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Ignition Policy', 'Spark plug ignition coil inspection procedure and replacement guidelines.', 10, 'hash_c_dense', TRUE)
                    """,
                    docIdDense
            );

            // Doc 2: Sparse match - long text where terms are separated by extensive filler prose
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Sparse Match Doc', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'sparse.txt', 'hash_sparse', 500, '{}'::jsonb, 'TEST')
                    """,
                    docIdSparse, codeSparse
            );
            String sparseText = "The facility maintains a standard warehouse protocol for automotive parts handling and inventory logistics. "
                    + "Staff members must verify that the spark plug packaging is sealed before staging. "
                    + "Various components such as radiators, exhaust pipes, gaskets, bushings, bearings, filters, belts, and hoses are stored separately. "
                    + "Any damaged ignition coil found during quarterly audits must be reported to the warehouse manager immediately.";
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'General Warehouse Procedures', ?, 60, 'hash_c_sparse', TRUE)
                    """,
                    docIdSparse, sparseText
            );

            List<RetrievedChunk> results = engine.retrieve("spark plug ignition coil", Set.of("PUBLIC"), 5);

            assertNotNull(results);
            assertTrue(results.size() >= 2, "Expected at least 2 results");

            int denseIndex = -1;
            int sparseIndex = -1;
            double denseScore = 0.0;
            double sparseScore = 0.0;

            for (int i = 0; i < results.size(); i++) {
                if (codeDense.equals(results.get(i).documentCode())) {
                    denseIndex = i;
                    denseScore = results.get(i).relevanceScore();
                } else if (codeSparse.equals(results.get(i).documentCode())) {
                    sparseIndex = i;
                    sparseScore = results.get(i).relevanceScore();
                }
            }

            assertTrue(denseIndex >= 0, "Dense document must be retrieved");
            assertTrue(sparseIndex >= 0, "Sparse document must be retrieved");
            assertTrue(denseIndex < sparseIndex, "Dense match must be ranked before sparse match due to cover density (flag 5)");
            assertTrue(denseScore > sparseScore, "Dense match score (" + denseScore + ") must be higher than sparse match score (" + sparseScore + ")");

        } finally {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id IN (?, ?)", docIdDense, docIdSparse);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id IN (?, ?)", docIdDense, docIdSparse);
            } finally {
                ds.destroy();
            }
        }
    }

    @Test
    @DisplayName("Verify double-active filtering: inactive documents or inactive chunks are never returned")
    void shouldExcludeInactiveDocumentsAndChunks() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        PostgresFtsRetrievalEngine engine = new PostgresFtsRetrievalEngine(jdbcTemplate);

        UUID docActive = UUID.randomUUID();
        UUID docInactive = UUID.randomUUID();
        String codeActive = "TEST-ACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String codeInactive = "TEST-INACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            // Document active, but Chunk inactive
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Active Doc Inactive Chunk', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'act.txt', 'h1', 100, '{}'::jsonb, 'TEST')
                    """,
                    docActive, codeActive
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Catalytic Converter', 'High efficiency catalytic converter recycling policy.', 10, 'h_c1', FALSE)
                    """,
                    docActive
            );

            // Document inactive, Chunk active
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Inactive Doc Active Chunk', 'POLICY', 'TEXT', 'PUBLIC', 1, FALSE, 'inact.txt', 'h2', 100, '{}'::jsonb, 'TEST')
                    """,
                    docInactive, codeInactive
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Catalytic Converter', 'High efficiency catalytic converter recycling policy.', 10, 'h_c2', TRUE)
                    """,
                    docInactive
            );

            List<RetrievedChunk> results = engine.retrieve("catalytic converter recycling", Set.of("PUBLIC"), 5);

            assertNotNull(results);
            boolean containsActiveDocInactiveChunk = results.stream().anyMatch(c -> codeActive.equals(c.documentCode()));
            boolean containsInactiveDocActiveChunk = results.stream().anyMatch(c -> codeInactive.equals(c.documentCode()));

            assertFalse(containsActiveDocInactiveChunk, "Inactive chunk must be excluded even if parent document is active");
            assertFalse(containsInactiveDocActiveChunk, "Chunks from inactive documents must be excluded even if chunk active flag is true");

        } finally {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id IN (?, ?)", docActive, docInactive);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id IN (?, ?)", docActive, docInactive);
            } finally {
                ds.destroy();
            }
        }
    }

    @Test
    @DisplayName("Verify SQL role filtering: documents requiring higher roles are excluded at the SQL level")
    void shouldEnforceSqlRoleFiltering() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        PostgresFtsRetrievalEngine engine = new PostgresFtsRetrievalEngine(jdbcTemplate);

        UUID docAdminId = UUID.randomUUID();
        String docAdminCode = "TEST-ADMIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Admin Sensitive Compensation Policy', 'POLICY', 'TEXT', 'ROLE_ADMIN', 1, TRUE, 'comp.txt', 'h_comp', 100, '{}'::jsonb, 'TEST')
                    """,
                    docAdminId, docAdminCode
            );
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_chunks (document_id, chunk_index, section_title, chunk_text, token_count, chunk_hash_sha256, active)
                    VALUES (?, 0, 'Executive Commission', 'Executive staff receive a five percent quarterly commission override on commercial sales.', 15, 'h_comm', TRUE)
                    """,
                    docAdminId
            );

            // Query as ROLE_CASHIER + PUBLIC (should NOT see ROLE_ADMIN document)
            List<RetrievedChunk> cashierResults = engine.retrieve(
                    "executive commission override commercial sales",
                    Set.of("ROLE_CASHIER", "PUBLIC"),
                    5
            );
            boolean cashierFound = cashierResults.stream().anyMatch(c -> docAdminCode.equals(c.documentCode()));
            assertFalse(cashierFound, "Cashier must NOT retrieve ROLE_ADMIN document");

            // Query as ROLE_ADMIN + PUBLIC (SHOULD see ROLE_ADMIN document)
            List<RetrievedChunk> adminResults = engine.retrieve(
                    "executive commission override commercial sales",
                    Set.of("ROLE_ADMIN", "PUBLIC"),
                    5
            );
            boolean adminFound = adminResults.stream().anyMatch(c -> docAdminCode.equals(c.documentCode()));
            assertTrue(adminFound, "Admin MUST retrieve ROLE_ADMIN document");

        } finally {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id = ?", docAdminId);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id = ?", docAdminId);
            } finally {
                ds.destroy();
            }
        }
    }

    @Test
    @DisplayName("Verify partial unique index uq_knowledge_documents_single_active_version rejects duplicate active versions")
    void shouldEnforceActiveVersionUniqueness() {
        Assumptions.assumeTrue(postgresAvailable, "Live PostgreSQL is not accessible. Skipping.");

        SingleConnectionDataSource ds = createDataSource();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);

        UUID docIdV1 = UUID.randomUUID();
        UUID docIdV2 = UUID.randomUUID();
        String sharedDocCode = "TEST-UNIQUE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            // Insert version 1 with active = TRUE
            jdbcTemplate.getJdbcTemplate().update(
                    """
                    INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                    VALUES (?, ?, 'Unique Version Test Doc v1', 'POLICY', 'TEXT', 'PUBLIC', 1, TRUE, 'v1.txt', 'h_v1', 100, '{}'::jsonb, 'TEST')
                    """,
                    docIdV1, sharedDocCode
            );

            // Attempting to insert version 2 with active = TRUE for the same document_code must violate uq_knowledge_documents_single_active_version
            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.getJdbcTemplate().update(
                        """
                        INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                        VALUES (?, ?, 'Unique Version Test Doc v2', 'POLICY', 'TEXT', 'PUBLIC', 2, TRUE, 'v2.txt', 'h_v2', 100, '{}'::jsonb, 'TEST')
                        """,
                        docIdV2, sharedDocCode
                );
            }, "Inserting two active versions of the same document_code must throw DataAccessException");

            // Deactivating version 1 allows version 2 with active = TRUE to succeed
            jdbcTemplate.getJdbcTemplate().update(
                    "UPDATE knowledge_documents SET active = FALSE WHERE id = ?",
                    docIdV1
            );

            assertDoesNotThrow(() -> {
                jdbcTemplate.getJdbcTemplate().update(
                        """
                        INSERT INTO knowledge_documents (id, document_code, title, document_type, content_format, required_role, version, active, file_name, file_hash_sha256, file_size_bytes, metadata, created_by)
                        VALUES (?, ?, 'Unique Version Test Doc v2', 'POLICY', 'TEXT', 'PUBLIC', 2, TRUE, 'v2.txt', 'h_v2', 100, '{}'::jsonb, 'TEST')
                        """,
                        docIdV2, sharedDocCode
                );
            }, "Deactivating previous version must allow activating new version");

        } finally {
            try {
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_chunks WHERE document_id IN (?, ?)", docIdV1, docIdV2);
                jdbcTemplate.getJdbcTemplate().update("DELETE FROM knowledge_documents WHERE id IN (?, ?)", docIdV1, docIdV2);
            } finally {
                ds.destroy();
            }
        }
    }
}
