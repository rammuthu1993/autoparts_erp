package com.autoparts.erp.config;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-Profile Runtime Startup Verification with Real Restricted AI Reader Account.
 *
 * Verifies that the full Spring Boot application successfully initializes under the "prod" profile:
 * 1. Primary datasource connects using a dedicated non-superuser ERP application account.
 * 2. AI reader datasource connects using the actual restricted autoparts_ai_reader account.
 * 3. Both connect to the dedicated test database only (autoparts_erp_test). Never connects to production.
 * 4. current_user and current_database() are verified on both connections.
 * 5. Reader connection is verified to be read-only.
 * 6. Reader SELECT succeeds on permitted business tables (products, purchases).
 * 7. Reader access to sensitive AI chat tables (ai_conversations, ai_messages) is strictly DENIED by PostgreSQL.
 * 8. Reader INSERT, UPDATE, and DELETE operations are rejected.
 * 9. Primary datasource remains writable.
 */
@EnabledIfSystemProperty(named = "run.production.profile.tests", matches = "true")
@SpringBootTest(classes = AutopartsErpApplication.class)
@ActiveProfiles("prod")
class ProductionProfileStartupIntegrationTest {

    private static final String TEST_DB_NAME = "autoparts_erp_test";
    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/" + TEST_DB_NAME;
    private static final String AI_READER_USER = "autoparts_ai_reader";
    private static String appRuntimeUser;

    @DynamicPropertySource
    static void configureProductionEnvironmentProperties(DynamicPropertyRegistry registry) {
        appRuntimeUser = requiredSetting("DB_USERNAME");
        String dbPass = requiredSecret("DB_PASSWORD");
        String aiReaderPass = requiredSecret("AI_TOOL_DB_PASSWORD");

        if ("postgres".equalsIgnoreCase(appRuntimeUser)) {
            throw new IllegalStateException("DB_USERNAME must be a dedicated non-superuser ERP runtime role, not postgres");
        }

        // Primary application datasource credentials
        registry.add("DB_URL", () -> TEST_DB_URL);
        registry.add("DB_USERNAME", () -> appRuntimeUser);
        registry.add("DB_PASSWORD", () -> dbPass);

        // AI reader datasource credentials using actual restricted account
        registry.add("AI_TOOL_DB_URL", () -> TEST_DB_URL);
        registry.add("AI_TOOL_DB_USERNAME", () -> AI_READER_USER);
        registry.add("AI_TOOL_DB_PASSWORD", () -> aiReaderPass);

        String validBase64Secret = Base64.getEncoder().encodeToString(
                "ProductionTestSecretKeyForJwtAuthenticationMustBeAtLeast32BytesLong123456".getBytes(StandardCharsets.UTF_8)
        );
        registry.add("JWT_SECRET", () -> validBase64Secret);
        registry.add("SARVAM_API_KEY", () -> "test-sarvam-key-placeholder");
        registry.add("APP_CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000,http://localhost:8080");

        // Preserve test database schema during this test
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    @Qualifier("dataSource")
    private DataSource primaryDataSource;

    @Autowired
    @Qualifier("aiToolDataSource")
    private DataSource aiToolDataSource;

    @Autowired
    @Qualifier("namedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate primaryNamedTemplate;

    @Autowired
    @Qualifier("aiToolJdbcTemplate")
    private JdbcTemplate aiToolJdbcTemplate;

    @Autowired
    private ProductLookupTool productLookupTool;

    @Test
    @DisplayName("Production Runtime: Active profile is 'prod', dual datasources initialized with distinct user roles")
    void testProductionProfileRuntimeInitialization() throws Exception {
        // 1. Verify active profile
        String[] activeProfiles = environment.getActiveProfiles();
        assertTrue(Arrays.asList(activeProfiles).contains("prod"),
                "Active profiles must contain 'prod'. Found: " + Arrays.toString(activeProfiles));

        // 2. Primary DataSource: verify connection, user, database, and writability
        assertNotNull(primaryDataSource, "Primary DataSource must be instantiated");
        assertTrue(primaryDataSource instanceof HikariDataSource);
        HikariDataSource primaryHikari = (HikariDataSource) primaryDataSource;
        assertFalse(primaryHikari.isReadOnly(), "Primary DataSource must NOT be read-only");

        try (Connection conn = primaryHikari.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT current_user, current_database()")) {
                assertTrue(rs.next());
                assertEquals(appRuntimeUser, rs.getString(1), "Primary connection must match configured ERP runtime user");
                assertEquals(TEST_DB_NAME, rs.getString(2), "Primary connection must connect to test DB");
            }
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT rolsuper FROM pg_roles WHERE rolname = current_user
                    """)) {
                assertTrue(rs.next(), "ERP runtime role must exist");
                assertFalse(rs.getBoolean(1), "ERP runtime role must not be a PostgreSQL superuser");
            }

            // Verify primary datasource remains writable
            conn.setAutoCommit(false);
            try {
                int updated = stmt.executeUpdate("UPDATE products SET updated_at = CURRENT_TIMESTAMP WHERE id = -9999");
                assertEquals(0, updated);
            } finally {
                conn.rollback();
            }
        }

        // 3. AI Tool Read-Only DataSource: verify connection, restricted user, database, and read-only mode
        assertNotNull(aiToolDataSource, "AI Tool DataSource must be instantiated");
        assertTrue(aiToolDataSource instanceof HikariDataSource);
        HikariDataSource aiHikari = (HikariDataSource) aiToolDataSource;
        assertTrue(aiHikari.isReadOnly(), "AI Tool DataSource must be read-only in prod profile");
        assertEquals("ai-tool-readonly-pool", aiHikari.getPoolName());

        try (Connection conn = aiHikari.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("""
                    SELECT current_user, current_database(), r.rolsuper
                    FROM pg_roles r WHERE r.rolname = current_user
                    """)) {
                assertTrue(rs.next());
                assertEquals(AI_READER_USER, rs.getString(1),
                        "AI reader connection user must strictly be '" + AI_READER_USER + "'");
                assertEquals(TEST_DB_NAME, rs.getString(2),
                        "AI reader connection must connect to dedicated test DB only");
                assertFalse(rs.getBoolean(3), "AI reader role must not be a PostgreSQL superuser");
            }

            assertTrue(conn.isReadOnly(), "Connection from AI DataSource must report isReadOnly() = true");

            // 4. Verify reader SELECT succeeds on permitted business tables
            assertDoesNotThrow(() -> {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM products")) {
                    assertTrue(rs.next());
                }
            }, "AI reader must have SELECT permission on 'products' table");

            assertDoesNotThrow(() -> {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM purchases")) {
                    assertTrue(rs.next());
                }
            }, "AI reader must have SELECT permission on 'purchases' table");

            // 5. Verify reader access to sensitive tables (ai_conversations, ai_messages) is strictly DENIED
            SQLException convEx = assertThrows(SQLException.class, () -> {
                stmt.executeQuery("SELECT count(*) FROM ai_conversations");
            }, "AI reader must be strictly denied SELECT permission on ai_conversations");
            assertTrue(convEx.getMessage().toLowerCase().contains("permission denied"),
                    "Error must be 'permission denied'. Actual: " + convEx.getMessage());

            SQLException msgEx = assertThrows(SQLException.class, () -> {
                stmt.executeQuery("SELECT count(*) FROM ai_messages");
            }, "AI reader must be strictly denied SELECT permission on ai_messages");
            assertTrue(msgEx.getMessage().toLowerCase().contains("permission denied"),
                    "Error must be 'permission denied'. Actual: " + msgEx.getMessage());

            // 6. Verify reader write operations (INSERT, UPDATE, DELETE) are rejected
            conn.setAutoCommit(false);
            try {
                assertThrows(SQLException.class, () -> {
                    stmt.executeUpdate("INSERT INTO products (name) VALUES ('unauthorized')");
                }, "AI reader must reject INSERT");

                assertThrows(SQLException.class, () -> {
                    stmt.executeUpdate("UPDATE products SET name = 'tampered' WHERE id = 1");
                }, "AI reader must reject UPDATE");

                assertThrows(SQLException.class, () -> {
                    stmt.executeUpdate("DELETE FROM products WHERE id = 1");
                }, "AI reader must reject DELETE");
            } finally {
                conn.rollback();
            }
        }

        // 7. Verify template and AI lookup tool injection
        assertNotNull(primaryNamedTemplate, "Primary NamedParameterJdbcTemplate must be wired");
        assertNotNull(aiToolJdbcTemplate, "AI Tool JdbcTemplate must be wired");
        assertNotNull(productLookupTool, "ProductLookupTool must be present and wired under prod profile");
    }

    private static String requiredSetting(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required production-profile test setting: " + key);
        }
        return value;
    }

    private static String requiredSecret(String key) {
        String value = resolveSecret(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required production-profile test credential: " + key);
        }
        return value;
    }

    private static String resolveSecret(String propertyKey) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && !systemValue.isBlank()) return systemValue;
        String envVal = System.getenv(propertyKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
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
        return null;
    }
}
