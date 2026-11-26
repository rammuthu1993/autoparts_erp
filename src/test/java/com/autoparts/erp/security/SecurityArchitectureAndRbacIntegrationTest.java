package com.autoparts.erp.security;

import com.autoparts.erp.AutopartsErpApplication;
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
import java.util.Properties;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("Spring Security 7 Architecture, HTTP Headers, and Role-Based Authorization Integration Tests")
class SecurityArchitectureAndRbacIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/autoparts_erp_test";
    private static final String ISSUER = "autoparts-erp";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUser = resolveSecret("DB_USERNAME", "postgres");
        String dbPass = resolveSecret("DB_PASSWORD", "postgres");
        String aiReaderPass = resolveSecret("AI_TOOL_DB_PASSWORD", "postgres");

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
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Autowired
    private JwtEncoder jwtEncoder;

    // =========================================================================
    // 1. Public Endpoint & Security Headers Verification
    // =========================================================================

    @Test
    @DisplayName("Public Endpoint: Health check is accessible anonymously and includes security headers and X-Correlation-ID")
    void testPublicHealthEndpointPermitsAnonymousAndIncludesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(header().string("Referrer-Policy", containsString("strict-origin")));
    }

    @Test
    @DisplayName("Public Endpoint: Login endpoint is accessible anonymously without bearer token")
    void testPublicLoginEndpointPermitsAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nonexistent\",\"password\":\"wrongpassword123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.error", containsString("Unauthorized")));
    }

    // =========================================================================
    // 2. Unauthenticated Protected Endpoint Rejection (401 Unauthorized)
    // =========================================================================

    @Test
    @DisplayName("Protected Endpoint: Rejects unauthenticated request with 401 Unauthorized and WWW-Authenticate: Bearer")
    void testUnauthenticatedProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // =========================================================================
    // 3. Client Correlation Header Propagation
    // =========================================================================

    @Test
    @DisplayName("Correlation Filter: Preserves and propagates valid client X-Correlation-ID")
    void testClientCorrelationHeaderPreserved() throws Exception {
        String clientTraceId = "client-trace-abc-123";

        mockMvc.perform(get("/api/v1/health")
                        .header("X-Correlation-ID", clientTraceId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", clientTraceId));
    }

    // =========================================================================
    // 4. Role-Based Access Control (RBAC) & Method Security
    // =========================================================================

    @Test
    @DisplayName("RBAC: Cashier token can read products but is FORBIDDEN (403) from creating or deactivating products")
    void testCashierCannotCreateProducts() throws Exception {
        String cashierToken = createBearerToken("cashier1", List.of("ROLE_CASHIER"), 101L);

        // Read is permitted for cashier
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        // Deactivating a product requires ADMIN or MANAGER
        mockMvc.perform(patch("/api/v1/products/" + java.util.UUID.randomUUID() + "/deactivate")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action."));

        // Creating product with valid payload is strictly forbidden for cashier
        String validProductJson = """
                {
                    "partNo": "BP-TEST-001",
                    "name": "Brake Pad Front",
                    "description": "Ceramic brake pad",
                    "categoryId": 1,
                    "brandId": 1,
                    "purchasePrice": 120.00,
                    "sellingPrice": 180.00,
                    "minimumStock": 5.000,
                    "unit": "SET"
                }
                """;
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("RBAC: Cashier token is FORBIDDEN (403) from receiving purchases")
    void testCashierCannotCreatePurchases() throws Exception {
        String cashierToken = createBearerToken("cashier1", List.of("ROLE_CASHIER"), 101L);

        mockMvc.perform(post("/api/v1/purchases/" + java.util.UUID.randomUUID() + "/receive")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("RBAC: Cashier token is FORBIDDEN (403) from closing business date")
    void testCashierCannotCreateTaxRates() throws Exception {
        String cashierToken = createBearerToken("cashier1", List.of("ROLE_CASHIER"), 101L);

        mockMvc.perform(post("/api/v1/daily-closings")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("businessDate", "2026-11-21"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("RBAC: Non-admin users are FORBIDDEN (403) from accessing User Management")
    void testNonAdminCannotAccessUserManagement() throws Exception {
        String managerToken = createBearerToken("manager1", List.of("ROLE_MANAGER"), 201L);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("RBAC: Admin token is permitted to access User Management")
    void testAdminCanAccessUserManagement() throws Exception {
        String adminToken = createBearerToken("admin1", List.of("ROLE_ADMIN"), 1L);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

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
