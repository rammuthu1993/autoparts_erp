package com.autoparts.erp.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all Flyway database migrations execute cleanly and sequentially
 * from V1 through the latest migration against a genuinely empty dedicated PostgreSQL test database.
 *
 * Verifies both:
 * 1. Fresh database migration from scratch (V1 to V34).
 * 2. Upgrade migration from existing V29 schema to V30+ with data preservation.
 *
 * Does NOT use pg_dump schema cloning.
 * NEVER connects to the development or production database.
 */
class FlywayFreshMigrationVerificationTest {

    private static final String TARGET_DB = "autoparts_erp_flyway_test";

    private static String setting(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        if (value == null || value.isBlank()) {
            if ("TEST_DB_URL".equals(key)) {
                return "jdbc:postgresql://localhost:5432/" + TARGET_DB;
            }
            if ("TEST_DB_USERNAME".equals(key)) {
                return resolveSecretProperty("TEST_DB_USERNAME", "flyway_test_user");
            }
            if ("TEST_DB_PASSWORD".equals(key)) {
                return resolveSecretProperty("DB_PASSWORD", "Muthu@1512");
            }
            throw new IllegalStateException(
                    "Missing required setting " + key
                    + ". Configure it explicitly for the destructive Flyway test.");
        }
        return value;
    }

    private static String resolveSecretProperty(String propertyKey, String defaultVal) {
        Path secretsPath = Path.of("config", "application-secrets.properties");
        if (Files.isRegularFile(secretsPath)) {
            try {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(secretsPath)) {
                    props.load(in);
                }
                if (props.containsKey(propertyKey)) {
                    return props.getProperty(propertyKey);
                }
            } catch (Exception ignored) {
            }
        }
        return defaultVal;
    }

    private static void assertSafeDatabaseSettings(String jdbcUrl, String dbUser) {
        assertTrue(jdbcUrl.matches(
                        "^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?/" + TARGET_DB + "(?:\\?.*)?$"),
                "TEST_DB_URL must point only to " + TARGET_DB);
        assertFalse(jdbcUrl.toLowerCase().contains("prod"),
                "Production-like JDBC URLs are forbidden");
        assertTrue(dbUser.matches("[A-Za-z_][A-Za-z0-9_]*"),
                "TEST_DB_USERNAME must be a simple PostgreSQL role identifier");
        assertFalse("postgres".equalsIgnoreCase(dbUser),
                "Destructive migration tests must not use the default postgres account");
    }

    private static void assertDedicatedTestDatabase(Connection conn) throws Exception {
        try (ResultSet rs = conn.createStatement().executeQuery("SELECT current_database(), current_setting('server_version_num')")) {
            assertTrue(rs.next());
            assertEquals(TARGET_DB, rs.getString(1), "Refusing destructive migration test outside " + TARGET_DB);
        }
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
            assertTrue(rs.next(), "Migration test role must exist");
            assertFalse(rs.getBoolean(1), "Destructive migration tests must not use a PostgreSQL superuser");
        }
        String jdbcUrl = setting("TEST_DB_URL");
        assertTrue(jdbcUrl.matches(
                        "^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1)(?::[0-9]+)?/" + TARGET_DB + "(?:\\?.*)?$"),
                "TEST_DB_URL must point exactly to " + TARGET_DB);
        assertFalse(jdbcUrl.toLowerCase().contains("prod"), "Production-like URL is forbidden");
    }

    @Test
    @EnabledIfSystemProperty(named = "run.destructive.flyway.tests", matches = "true")
    @DisplayName("Flyway Fresh Migration: Complete sequential migration from V1 to V34 against empty database")
    void testFreshFlywayMigrationChain() throws Exception {
        String jdbcUrl = setting("TEST_DB_URL");
        String dbUser = setting("TEST_DB_USERNAME");
        String dbPass = setting("TEST_DB_PASSWORD");
        assertSafeDatabaseSettings(jdbcUrl, dbUser);

        // 1. Safety check
        assertTrue(TARGET_DB.contains("test"), "Safety violation: target DB must contain 'test'");
        assertFalse(TARGET_DB.equals("autoparts_erp"), "Safety violation: target DB must never be production/development DB");

        // 2. Clean public schema for a genuine fresh run
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {

            assertDedicatedTestDatabase(conn);
            stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
            stmt.execute("CREATE SCHEMA public");
            stmt.execute("GRANT ALL ON SCHEMA public TO \"" + dbUser + "\"");
            stmt.execute("GRANT ALL ON SCHEMA public TO public");
        }

        // 3. Configure and execute Flyway
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.success, "Flyway migration must succeed from V1 through latest");
        assertTrue(result.migrationsExecuted >= 30,
                "All migrations must be executed. Count: " + result.migrationsExecuted);

        // 4. Verify all applied migrations have status SUCCESS
        MigrationInfo[] appliedMigrations = flyway.info().applied();
        assertTrue(appliedMigrations.length >= 33, "All 33 migrations must be applied. Found: " + appliedMigrations.length);

        for (MigrationInfo info : appliedMigrations) {
            assertEquals("SUCCESS", info.getState().name(),
                    "Migration " + info.getScript() + " must be in SUCCESS state");
        }

        // 5. Verify final ai_agent_runs schema: columns, constraints, foreign keys, indexes
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {

            // Column verification
            try (ResultSet rs = stmt.executeQuery("""
                SELECT column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ai_agent_runs'
                ORDER BY ordinal_position
            """)) {
                boolean hasUserSubject = false;
                boolean hasAttemptCount = false;
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    String nullable = rs.getString("is_nullable");
                    if ("user_subject".equals(col)) {
                        hasUserSubject = true;
                        assertEquals("NO", nullable, "user_subject must be NOT NULL");
                    }
                    if ("attempt_count".equals(col)) {
                        hasAttemptCount = true;
                        assertEquals("NO", nullable, "attempt_count must be NOT NULL");
                    }
                }
                assertTrue(hasUserSubject, "ai_agent_runs must have user_subject column");
                assertTrue(hasAttemptCount, "ai_agent_runs must have attempt_count column");
            }

            // Check constraints verification
            try (ResultSet rs = stmt.executeQuery("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'ai_agent_runs'::regclass AND contype = 'c'
            """)) {
                boolean hasStatusCheck = false;
                boolean hasAttemptCheck = false;
                while (rs.next()) {
                    String con = rs.getString(1);
                    if ("chk_ai_agent_runs_status".equals(con)) hasStatusCheck = true;
                    if ("chk_ai_agent_runs_attempt_count".equals(con)) hasAttemptCheck = true;
                }
                assertTrue(hasStatusCheck, "chk_ai_agent_runs_status constraint must exist");
                assertTrue(hasAttemptCheck, "chk_ai_agent_runs_attempt_count constraint must exist");
            }

            // Indexes verification
            try (ResultSet rs = stmt.executeQuery("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'ai_agent_runs'
            """)) {
                boolean hasConvIdx = false;
                boolean hasUserIdx = false;
                boolean hasStatusIdx = false;
                while (rs.next()) {
                    String idx = rs.getString(1);
                    if ("idx_ai_agent_runs_conversation".equals(idx)) hasConvIdx = true;
                    if ("idx_ai_agent_runs_user".equals(idx)) hasUserIdx = true;
                    if ("idx_ai_agent_runs_status".equals(idx)) hasStatusIdx = true;
                }
                assertTrue(hasConvIdx, "idx_ai_agent_runs_conversation index must exist");
                assertTrue(hasUserIdx, "idx_ai_agent_runs_user index must exist");
                assertTrue(hasStatusIdx, "idx_ai_agent_runs_status index must exist");
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "run.destructive.flyway.tests", matches = "true")
    @DisplayName("Flyway Upgrade Migration: Existing database with V29 data successfully upgrades to V30+ without data loss")
    void testUpgradeMigrationFromV29WithDataPreservation() throws Exception {
        String jdbcUrl = setting("TEST_DB_URL");
        String dbUser = setting("TEST_DB_USERNAME");
        String dbPass = setting("TEST_DB_PASSWORD");
        assertSafeDatabaseSettings(jdbcUrl, dbUser);

        // 1. Clean public schema
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {
            assertDedicatedTestDatabase(conn);
            stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
            stmt.execute("CREATE SCHEMA public");
            stmt.execute("GRANT ALL ON SCHEMA public TO \"" + dbUser + "\"");
            stmt.execute("GRANT ALL ON SCHEMA public TO public");
        }

        // 2. Migrate only up to V29
        Flyway flywayV29 = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .target("29")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        MigrateResult resultV29 = flywayV29.migrate();
        assertTrue(resultV29.success);

        // 3. Insert pre-existing conversation and ai_agent_run record under V29 schema
        UUID convId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement()) {

            stmt.execute("INSERT INTO ai_conversations (id, user_subject, title) VALUES ('" + convId + "', 'pre-existing-user', 'Pre-V30 chat')");
            stmt.execute("INSERT INTO ai_agent_runs (id, conversation_id, status, attempt_count) VALUES ('" + runId + "', '" + convId + "', 'RUNNING', 0)");
        }

        // 4. Now run full migration to latest (applying V30, V32, V33, V34)
        Flyway flywayFull = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPass)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        MigrateResult resultFull = flywayFull.migrate();
        assertTrue(resultFull.success, "Upgrade from V29 to latest must succeed");

        // 5. Verify that existing data in ai_agent_runs is fully preserved and upgraded
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, user_subject, status, attempt_count FROM ai_agent_runs WHERE id = '" + runId + "'")) {

            assertTrue(rs.next(), "Pre-existing ai_agent_run row must be preserved");
            assertEquals(runId.toString(), rs.getString("id"));
            assertEquals("SYSTEM", rs.getString("user_subject"), "Existing rows without user_subject must be backfilled to 'SYSTEM'");
            assertEquals("RUNNING", rs.getString("status"));
            assertTrue(rs.getInt("attempt_count") >= 1, "attempt_count < 1 must be adjusted to 1 for constraint compliance");
        }
    }

}
