package com.autoparts.erp.config;

import com.autoparts.erp.AutopartsErpApplication;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production-Profile Runtime Startup with Flyway Enabled.
 *
 * Verifies that the full Spring Boot application initializes under the "prod" profile
 * with live Flyway validation and migration execution enabled against the dedicated test database:
 * 1. spring.flyway.enabled=true
 * 2. spring.flyway.validate-on-migrate=true
 * 3. spring.flyway.baseline-on-migrate=false
 * 4. spring.flyway.clean-disabled=true
 * 5. Flyway bean successfully executes and validates all migrations against the test database.
 * 6. Never connects to production.
 */
@EnabledIfSystemProperty(named = "run.production.profile.tests", matches = "true")
@SpringBootTest(classes = AutopartsErpApplication.class)
@ActiveProfiles("prod")
class ProductionProfileFlywayStartupIntegrationTest {

    private static final String TARGET_DB = "autoparts_erp_flyway_test";
    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/" + TARGET_DB;
    private static final String AI_READER_USER = "autoparts_ai_reader";
    private static String appRuntimeUser;
    private static String flywayMigrationUser;
    private static String flywayMigrationPassword;

    @DynamicPropertySource
    static void configureProductionPropertiesWithFlyway(DynamicPropertyRegistry registry) {
        appRuntimeUser = requiredSetting("DB_USERNAME");
        String dbPass = requiredSecret("DB_PASSWORD");
        String aiReaderPass = requiredSecret("AI_TOOL_DB_PASSWORD");
        String flywayUser = requiredSetting("FLYWAY_DB_USERNAME");
        String flywayPass = requiredSecret("FLYWAY_DB_PASSWORD");
        flywayMigrationUser = flywayUser;
        flywayMigrationPassword = flywayPass;

        if ("postgres".equalsIgnoreCase(appRuntimeUser) || "postgres".equalsIgnoreCase(flywayUser)) {
            throw new IllegalStateException("Production-profile tests must use non-superuser ERP and Flyway roles");
        }
        if (appRuntimeUser.equals(flywayUser)) {
            throw new IllegalStateException("FLYWAY_DB_USERNAME must be distinct from DB_USERNAME");
        }
        if (AI_READER_USER.equals(flywayUser)) {
            throw new IllegalStateException("Flyway migration role must be distinct from the AI reader role");
        }

        registry.add("DB_URL", () -> TEST_DB_URL);
        registry.add("DB_USERNAME", () -> appRuntimeUser);
        registry.add("DB_PASSWORD", () -> dbPass);
        registry.add("spring.flyway.user", () -> flywayUser);
        registry.add("spring.flyway.password", () -> flywayPass);

        registry.add("AI_TOOL_DB_URL", () -> TEST_DB_URL);
        registry.add("AI_TOOL_DB_USERNAME", () -> AI_READER_USER);
        registry.add("AI_TOOL_DB_PASSWORD", () -> aiReaderPass);

        String validBase64Secret = Base64.getEncoder().encodeToString(
                "ProductionFlywayTestSecretKeyMustBeAtLeast32BytesLong123456789".getBytes(StandardCharsets.UTF_8)
        );
        registry.add("JWT_SECRET", () -> validBase64Secret);
        registry.add("SARVAM_API_KEY", () -> "test-sarvam-key-placeholder");
        registry.add("APP_CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");

        // Explicitly enable Flyway in prod profile
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.validate-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
    }

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private Flyway flyway;

    @Autowired
    @Qualifier("dataSource")
    private DataSource primaryDataSource;

    @Test
    @DisplayName("Production Startup with Flyway: Flyway validates all 33 migrations and app starts in prod profile")
    void testProductionStartupWithFlywayEnabled() throws Exception {
        // 1. Verify profile
        assertTrue(Arrays.asList(environment.getActiveProfiles()).contains("prod"));

        // 2. Verify Flyway bean and migration history
        assertNotNull(flyway, "Flyway bean must be initialized when spring.flyway.enabled=true");
        assertDoesNotThrow(() -> flyway.validate(), "Flyway validation must pass without errors in prod profile");

        MigrationInfo[] applied = flyway.info().applied();
        assertTrue(applied.length >= 33, "All 33 migrations must be applied. Found: " + applied.length);

        // 3. Verify safety: connected to TARGET_DB, never production
        try (Connection conn = primaryDataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertEquals(TARGET_DB, rs.getString(1), "Must strictly connect to dedicated test DB");
        }
        try (Connection conn = primaryDataSource.getConnection(); var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT current_user, current_database(), r.rolsuper
                     FROM pg_roles r WHERE r.rolname = current_user
                     """)) {
            assertTrue(rs.next());
            assertEquals(appRuntimeUser, rs.getString(1));
            assertEquals(TARGET_DB, rs.getString(2));
            assertFalse(rs.getBoolean(3), "ERP runtime role must not be a PostgreSQL superuser");
        }

        // Verify the separate migration-owner credential is valid and non-superuser.
        try (Connection conn = java.sql.DriverManager.getConnection(
                     TEST_DB_URL, flywayMigrationUser, flywayMigrationPassword);
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT current_user, current_database(), r.rolsuper
                     FROM pg_roles r WHERE r.rolname = current_user
                     """)) {
            assertTrue(rs.next());
            assertEquals(flywayMigrationUser, rs.getString(1));
            assertEquals(TARGET_DB, rs.getString(2));
            assertFalse(rs.getBoolean(3), "Flyway migration role must not be a PostgreSQL superuser");
        }
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
