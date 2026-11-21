package com.autoparts.erp.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production Configuration Verification Test.
 *
 * Verifies that application-prod.properties adheres to all enterprise production standards:
 * 1. Zero hardcoded secrets, passwords, or tokens.
 * 2. All deployment credentials are environment-variable driven via ${VAR} placeholders.
 * 3. Database connection pools have conservative limits configured.
 * 4. Flyway clean is disabled and baselining is strict.
 * 5. Production error responses never disclose stack traces, messages, or exceptions.
 * 6. AI lookup tool datasource credentials are strictly segregated from ERP primary credentials.
 * 7. AI tool audit logging is configured to fail closed.
 */
class ProductionConfigurationVerificationTest {

    private static final Path PROD_CONFIG_PATH = Path.of("src", "main", "resources", "application-prod.properties");
    private static Properties prodProperties;
    private static List<String> prodLines;

    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\{([A-Za-z0-9_]+)(?::(.*))?\\}$");

    @BeforeAll
    static void loadProperties() throws IOException {
        assertTrue(Files.isRegularFile(PROD_CONFIG_PATH),
                "application-prod.properties must exist at src/main/resources/application-prod.properties");

        prodLines = Files.readAllLines(PROD_CONFIG_PATH);
        prodProperties = new Properties();
        try (InputStream in = Files.newInputStream(PROD_CONFIG_PATH)) {
            prodProperties.load(in);
        }
    }

    @Test
    @DisplayName("Production Security: Zero plaintext secrets in application-prod.properties")
    void testZeroHardcodedSecrets() {
        List<String> sensitiveKeys = List.of(
                "password", "secret", "key", "token", "credential"
        );

        for (String key : prodProperties.stringPropertyNames()) {
            String lowerKey = key.toLowerCase();
            boolean isSensitive = sensitiveKeys.stream().anyMatch(lowerKey::contains);
            if (isSensitive) {
                String val = prodProperties.getProperty(key).trim();
                Matcher m = ENV_PLACEHOLDER_PATTERN.matcher(val);
                assertTrue(m.matches(),
                        "Property '" + key + "' contains literal or unformatted value '" + val +
                                "'. Production secrets must strictly use ${ENV_VAR} format.");

                // Ensure the variable name itself is not empty
                String envVar = m.group(1);
                assertFalse(envVar.isBlank(), "Environment variable placeholder for " + key + " is empty");
            }
        }
    }

    @Test
    @DisplayName("Datasource Segregation: AI tool database credentials are distinct from primary ERP credentials")
    void testDatasourceCredentialSegregation() {
        String primaryUrl = prodProperties.getProperty("spring.datasource.url");
        String primaryUser = prodProperties.getProperty("spring.datasource.username");
        String primaryPass = prodProperties.getProperty("spring.datasource.password");

        String aiUrl = prodProperties.getProperty("app.ai.datasource.url");
        String aiUser = prodProperties.getProperty("app.ai.datasource.username");
        String aiPass = prodProperties.getProperty("app.ai.datasource.password");

        assertNotNull(primaryUrl, "spring.datasource.url must be defined");
        assertNotNull(primaryUser, "spring.datasource.username must be defined");
        assertNotNull(primaryPass, "spring.datasource.password must be defined");

        assertNotNull(aiUrl, "app.ai.datasource.url must be defined");
        assertNotNull(aiUser, "app.ai.datasource.username must be defined");
        assertNotNull(aiPass, "app.ai.datasource.password must be defined");

        // The placeholder environment variable names must differ to enforce distinct accounts
        assertNotEquals(primaryUser, aiUser,
                "AI tool username env placeholder must be distinct from primary ERP username env placeholder");
        assertNotEquals(primaryPass, aiPass,
                "AI tool password env placeholder must be distinct from primary ERP password env placeholder");
    }

    @Test
    @DisplayName("Flyway Safeguards: clean is disabled, validate is enabled, baseline-on-migrate is disabled")
    void testFlywayProductionSafeguards() {
        assertEquals("true", prodProperties.getProperty("spring.flyway.clean-disabled"),
                "Flyway clean must be strictly disabled in production");
        assertEquals("false", prodProperties.getProperty("spring.flyway.baseline-on-migrate"),
                "Flyway baseline-on-migrate must be false in production");
        assertEquals("true", prodProperties.getProperty("spring.flyway.validate-on-migrate"),
                "Flyway validate-on-migrate must be true in production");
    }

    @Test
    @DisplayName("Error Disclosure: All error message and stacktrace exposures are disabled")
    void testErrorDisclosurePrevention() {
        assertEquals("never", prodProperties.getProperty("server.error.include-message"),
                "server.error.include-message must be 'never'");
        assertEquals("never", prodProperties.getProperty("server.error.include-binding-errors"),
                "server.error.include-binding-errors must be 'never'");
        assertEquals("never", prodProperties.getProperty("server.error.include-stacktrace"),
                "server.error.include-stacktrace must be 'never'");
        assertEquals("false", prodProperties.getProperty("server.error.include-exception"),
                "server.error.include-exception must be 'false'");
        assertEquals("never", prodProperties.getProperty("management.endpoint.health.show-details"),
                "management.endpoint.health.show-details must be 'never'");
    }

    @Test
    @DisplayName("Audit & Compliance: AI tool audit logging must fail-closed")
    void testAuditFailClosedSetting() {
        String failClosed = prodProperties.getProperty("app.ai.audit.fail-closed");
        assertNotNull(failClosed, "app.ai.audit.fail-closed must be defined");
        assertTrue(failClosed.contains("true"),
                "app.ai.audit.fail-closed must default to true (e.g. ${AI_AUDIT_FAIL_CLOSED:true})");
    }

    @Test
    @DisplayName("Connection Pool: Hikari pool limits are explicitly configured")
    void testConnectionPoolLimits() {
        assertNotNull(prodProperties.getProperty("spring.datasource.hikari.maximum-pool-size"));
        assertNotNull(prodProperties.getProperty("spring.datasource.hikari.minimum-idle"));
        assertNotNull(prodProperties.getProperty("spring.datasource.hikari.connection-timeout"));
        assertNotNull(prodProperties.getProperty("spring.datasource.hikari.max-lifetime"));
    }
}
