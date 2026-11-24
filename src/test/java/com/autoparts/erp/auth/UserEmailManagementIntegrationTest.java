package com.autoparts.erp.auth;

import com.autoparts.erp.AutopartsErpApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("User Email Management Integration Tests")
class UserEmailManagementIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/autoparts_erp_test";
    private static final String ISSUER = "autoparts-erp";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUser = resolveSecret("DB_USERNAME", "postgres");
        String dbPass = resolveSecret("DB_PASSWORD", "postgres");
        String aiReaderPass = resolveSecret("AI_TOOL_DB_PASSWORD", "Muthu@1512");

        registry.add("DB_URL", () -> TEST_DB_URL);
        registry.add("DB_USERNAME", () -> dbUser);
        registry.add("DB_PASSWORD", () -> dbPass);

        registry.add("AI_TOOL_DB_URL", () -> TEST_DB_URL);
        registry.add("AI_TOOL_DB_USERNAME", () -> "autoparts_ai_reader");
        registry.add("AI_TOOL_DB_PASSWORD", () -> aiReaderPass);

        String validBase64Secret = Base64.getEncoder().encodeToString(
                "SecurityTestJwtSecretMustBeAtLeast32BytesLong123456789".getBytes(StandardCharsets.UTF_8)
        );
        registry.add("JWT_SECRET", () -> validBase64Secret);
        registry.add("SARVAM_API_KEY", () -> "test-sarvam-key-placeholder");
        registry.add("APP_CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000,http://localhost:8080");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.mail.enabled", () -> "false");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("jdbcTemplate")
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.autoparts.erp.auth.repository.UserManagementRepository userManagementRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Long adminUserId;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        jdbcTemplate.execute("""
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

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        if (adminUserId == null) {
            String adminUsername = "admin_it_" + UUID.randomUUID().toString().substring(0, 6);
            adminUserId = userManagementRepository.create(
                    adminUsername,
                    adminUsername + "@example.com",
                    passwordEncoder.encode("AdminPass12345!"),
                    UUID.randomUUID()
            );
            userManagementRepository.replaceRoles(adminUserId, Set.of("ADMIN"));
        }
    }

    @Test
    @DisplayName("Admin creates user with valid email; email is normalized and unverified by default")
    void testCreateUserWithValidEmail() throws Exception {
        String adminToken = createBearerToken("admin", List.of("ROLE_ADMIN"), adminUserId);
        String username = "email_user_" + UUID.randomUUID().toString().substring(0, 6);
        String rawEmail = "  User." + username + "@EXAMPLE.Com  ";

        Map<String, Object> request = Map.of(
                "username", username,
                "email", rawEmail,
                "password", "Password12345!",
                "roleNames", Set.of("CASHIER")
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is(username)))
                .andExpect(jsonPath("$.email", is("user." + username.toLowerCase() + "@example.com")))
                .andExpect(jsonPath("$.emailVerified", is(false)))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Admin user creation rejects invalid email format with 400 Bad Request")
    void testCreateUserWithInvalidEmail() throws Exception {
        String adminToken = createBearerToken("admin", List.of("ROLE_ADMIN"), adminUserId);
        String username = "invalid_email_" + UUID.randomUUID().toString().substring(0, 6);

        Map<String, Object> request = Map.of(
                "username", username,
                "email", "not-an-email-address",
                "password", "Password12345!",
                "roleNames", Set.of("CASHIER")
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Admin user creation rejects duplicate email case-insensitively with 409 Conflict")
    void testCreateUserWithDuplicateEmailCaseInsensitive() throws Exception {
        String adminToken = createBearerToken("admin", List.of("ROLE_ADMIN"), adminUserId);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        String sharedEmail = "duplicate." + uniqueSuffix + "@example.com";

        Map<String, Object> request1 = Map.of(
                "username", "user1_" + uniqueSuffix,
                "email", sharedEmail.toLowerCase(),
                "password", "Password12345!",
                "roleNames", Set.of("CASHIER")
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        Map<String, Object> request2 = Map.of(
                "username", "user2_" + uniqueSuffix,
                "email", sharedEmail.toUpperCase(),
                "password", "Password12345!",
                "roleNames", Set.of("CASHIER")
        );

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Email already registered")));
    }

    @Test
    @DisplayName("Admin updates user email; resets email_verified to false")
    void testUpdateUserEmailResetsVerification() throws Exception {
        String adminToken = createBearerToken("admin", List.of("ROLE_ADMIN"), adminUserId);
        String username = "upd_user_" + UUID.randomUUID().toString().substring(0, 6);
        String initialEmail = username + "@example.com";

        Map<String, Object> createReq = Map.of(
                "username", username,
                "email", initialEmail,
                "password", "Password12345!",
                "roleNames", Set.of("CASHIER")
        );

        String createRes = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String uuid = objectMapper.readTree(createRes).get("uuid").asText();

        // Update email to a new address
        String newEmail = "new." + username + "@example.com";
        Map<String, Object> updateReq = Map.of(
                "username", username,
                "email", newEmail
        );

        mockMvc.perform(put("/api/v1/admin/users/" + uuid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(newEmail)))
                .andExpect(jsonPath("$.emailVerified", is(false)));

        // Retrieve user by UUID
        mockMvc.perform(get("/api/v1/admin/users/" + uuid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(newEmail)))
                .andExpect(jsonPath("$.emailVerified", is(false)));
    }

    private String createBearerToken(String username, List<String> roles, Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(username)
                .claim("roles", roles)
                .claim("user_id", userId)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String resolveSecret(String propertyKey, String defaultVal) {
        String envVal = System.getenv(propertyKey);
        if (envVal != null && !envVal.isBlank()) return envVal;
        Path secretsPath = Path.of("config", "application-secrets.properties");
        if (Files.isRegularFile(secretsPath)) {
            try {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(secretsPath)) {
                    props.load(in);
                }
                if (props.containsKey(propertyKey)) return props.getProperty(propertyKey);
            } catch (Exception ignored) {
            }
        }
        return defaultVal;
    }
}
