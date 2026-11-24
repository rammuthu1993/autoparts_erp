package com.autoparts.erp.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reusable base class for PostgreSQL integration tests against all 25 ERP repositories.
 * Connects directly to the dedicated PostgreSQL test database (autoparts_erp_test)
 * on PostgreSQL 18.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseRepositoryIntegrationTest {

    protected static String dbUrl = System.getProperty("TEST_DB_URL", System.getenv("TEST_DB_URL"));
    protected static String dbUser = System.getProperty("TEST_DB_USERNAME", System.getenv("TEST_DB_USERNAME"));
    protected static String dbPass = System.getProperty("TEST_DB_PASSWORD", System.getenv("TEST_DB_PASSWORD"));

    private static HikariDataSource dataSource;
    private static NamedParameterJdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static boolean initialized = false;

    private static final Pattern JDBC_PG_HOST_PORT_PATTERN =
            Pattern.compile("^(?:jdbc:)?postgresql://(?:[^/@]+@)?(?:\\[([^\\]]+)\\]|([^:/?#]+))(?::([0-9]+))?");

    @BeforeAll
    public static synchronized void setUpDatabaseConnection() {
        if (initialized) {
            return;
        }

        resolveCredentials();
        validateSafeTestDatabase(dbUrl);

        HostPort hostPort = parseHostAndPort(dbUrl);
        if (!isReachable(hostPort.host, hostPort.port, 2000)) {
            fail("PostgreSQL test service is unreachable at " + hostPort.host + ":" + hostPort.port
                    + ". Check if local PostgreSQL is running on port 5432.");
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPass);
            config.setDriverClassName("org.postgresql.Driver");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(10000);
            config.setPoolName("erp-integration-test-pool");

            dataSource = new HikariDataSource(config);
            jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
            transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

            // Verify connectivity and test database name
            String currentDb = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT current_database()", String.class
            );
            assertNotNull(currentDb);
            if (!currentDb.toLowerCase().contains("test")) {
                fail("Safety violation: Current database '" + currentDb + "' does not contain 'test'. Refusing to run tests.");
            }

            // Ensure baseline reference data from migrations is seeded if absent
            seedBaselineDataIfMissing();

            initialized = true;
        } catch (Exception e) {
            fail("Failed to connect to PostgreSQL test database: " + e.getMessage(), e);
        }
    }

    private static void seedBaselineDataIfMissing() {
        jdbcTemplate.getJdbcTemplate().execute("""
            INSERT INTO roles (uuid, name, description)
            VALUES
                ('11111111-1111-4111-8111-111111111111', 'ADMIN', 'Full ERP administration access'),
                ('22222222-2222-4222-8222-222222222222', 'MANAGER', 'ERP management access'),
                ('33333333-3333-4333-8333-333333333333', 'CASHIER', 'Sales and billing access')
            ON CONFLICT (name) DO NOTHING;

            ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(254) NULL;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
            CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (LOWER(TRIM(email))) WHERE email IS NOT NULL;

            CREATE TABLE IF NOT EXISTS user_security_tokens (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                token_hash VARCHAR(128) NOT NULL,
                purpose VARCHAR(50) NOT NULL,
                expires_at TIMESTAMPTZ NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                max_attempts INTEGER NOT NULL DEFAULT 5,
                resend_available_at TIMESTAMPTZ NULL,
                consumed_at TIMESTAMPTZ NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_user_security_tokens_user_purpose ON user_security_tokens (user_id, purpose) WHERE consumed_at IS NULL;
            CREATE INDEX IF NOT EXISTS idx_user_security_tokens_hash_purpose ON user_security_tokens (token_hash, purpose) WHERE consumed_at IS NULL;
            CREATE INDEX IF NOT EXISTS idx_user_security_tokens_expires_at ON user_security_tokens (expires_at);
        """);
    }

    private static void resolveCredentials() {
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://localhost:5432/autoparts_erp_test";
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
                } catch (Exception ignored) {
                }
            }
        }
        if (dbPass == null) {
            dbPass = "";
        }
    }

    private static void validateSafeTestDatabase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Test database URL is null or empty");
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("prod") || lowerUrl.contains("live-db")) {
            throw new IllegalStateException("Safety rule violation: refusing to run against production database target: " + url);
        }
        if (!lowerUrl.contains("test")) {
            throw new IllegalStateException("Safety rule violation: test database URL must target a test database (e.g., autoparts_erp_test): " + url);
        }
    }

    private static boolean isReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static HostPort parseHostAndPort(String url) {
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

    private record HostPort(String host, int port) {}

    public DataSource getDataSource() {
        return dataSource;
    }

    public NamedParameterJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public TransactionTemplate getTransactionTemplate() {
        return transactionTemplate;
    }

    public Connection openIndependentConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected UUID randomUuid() {
        return UUID.randomUUID();
    }
}
