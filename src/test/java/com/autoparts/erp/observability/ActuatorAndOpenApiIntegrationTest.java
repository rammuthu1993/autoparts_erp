package com.autoparts.erp.observability;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("Phase P3: OpenAPI 3 Documentation and Prometheus Observability Integration Tests")
class ActuatorAndOpenApiIntegrationTest {

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
        registry.add("management.endpoints.web.exposure.include", () -> "health,info,metrics,prometheus");
        registry.add("management.endpoint.health.show-details", () -> "never");
        registry.add("management.endpoint.health.probes.enabled", () -> "true");
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("management.endpoint.prometheus.enabled", () -> "true");
        registry.add("management.prometheus.metrics.export.enabled", () -> "true");
        registry.add("management.metrics.export.prometheus.enabled", () -> "true");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // =========================================================================
    // 1. OpenAPI 3 Specification & Swagger UI Tests
    // =========================================================================

    @Test
    @DisplayName("OpenAPI 3: /v3/api-docs is publicly accessible and returns valid OpenAPI 3 JSON definition")
    void testApiDocsEndpointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Autoparts ERP REST API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    @DisplayName("OpenAPI 3: Public endpoints have empty security requirements (security: []) while global security has bearerAuth")
    void testOpenApiPublicEndpointsSecurityRequirements() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/forgot-password/request-otp'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/forgot-password/verify-otp'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/forgot-password/reset'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/verify-email/confirm'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/change-password'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/sales/paged'].get.security").doesNotExist());
    }

    @Test
    @DisplayName("Swagger UI: /swagger-ui.html or /swagger-ui/index.html is publicly accessible")
    void testSwaggerUiIsAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }

    // =========================================================================
    // 2. Actuator Health & Prometheus Metrics Tests
    // =========================================================================

    @Test
    @DisplayName("Actuator: /actuator/health is publicly accessible and reports UP without exposing internal components")
    void testActuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("Actuator: /actuator/health/liveness and /actuator/health/readiness probes are publicly accessible")
    void testActuatorHealthProbesArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator & API: /actuator/info and /api/v1/health are publicly accessible")
    void testActuatorInfoAndApplicationHealthArePublic() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("autoparts-erp"));
    }

    @Test
    @DisplayName("Prometheus: /actuator/prometheus rejects unauthenticated requests with 401 Unauthorized")
    void testActuatorPrometheusRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Prometheus: /actuator/prometheus rejects non-admin users (ROLE_CASHIER) with 403 Forbidden")
    void testActuatorPrometheusRejectsCashier() throws Exception {
        String cashierToken = createBearerToken("cashier1", List.of("ROLE_CASHIER"), 101L);

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Prometheus: /actuator/prometheus permits ROLE_ADMIN and exposes metrics in Prometheus text format")
    void testActuatorPrometheusPermitsAdmin() throws Exception {
        String adminToken = createBearerToken("admin", List.of("ROLE_ADMIN"), 1L);

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_")))
                .andExpect(content().string(containsString("application=\"autoparts-erp\"")));
    }

    // =========================================================================
    // Helpers
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
