package com.autoparts.erp.masters.brand;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("BrandResponse UUID Contract & API Integration Tests")
class BrandUuidContractIntegrationTest {

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

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserManagementRepository userManagementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private String adminToken;
    private String cashierToken;

    @BeforeEach
    void setUpMockMvcAndTokens() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        // Seed roles & permissions in test DB
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS product_brands (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
                name VARCHAR(100) NOT NULL,
                description VARCHAR(500),
                active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS permissions (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
                code VARCHAR(100) NOT NULL UNIQUE,
                name VARCHAR(150) NOT NULL,
                description VARCHAR(500),
                module VARCHAR(50) NOT NULL,
                action VARCHAR(50) NOT NULL,
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS role_permissions (
                role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
                assigned_by BIGINT NULL REFERENCES users(id) ON DELETE SET NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (role_id, permission_id)
            );

            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('BRANDS.VIEW', 'View Brands', 'View brands', 'BRANDS', 'VIEW'),
                ('BRANDS.CREATE', 'Create Brands', 'Create brands', 'BRANDS', 'CREATE'),
                ('BRANDS.UPDATE', 'Update Brands', 'Update brands', 'BRANDS', 'UPDATE'),
                ('BRANDS.DEACTIVATE', 'Deactivate Brands', 'Deactivate brands', 'BRANDS', 'DEACTIVATE')
            ON CONFLICT (code) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'CASHIER' AND p.code IN ('BRANDS.VIEW')
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        String adminUser = "brand_adm_" + UUID.randomUUID().toString().substring(0, 8);
        UUID adminUuid = UUID.randomUUID();
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("Test@123"), adminUuid);
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));
        adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        String cashierUser = "brand_csh_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cashierUuid = UUID.randomUUID();
        Long cashierId = userManagementRepository.create(cashierUser, cashierUser + "@example.com", passwordEncoder.encode("Test@123"), cashierUuid);
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));
        cashierToken = createBearerToken(cashierUser, List.of("CASHIER"), cashierId);
    }

    @Test
    @DisplayName("1. Brand creation returns a non-null database UUID matching the database record")
    void testBrandCreationReturnsNonNullUuid() throws Exception {
        String brandName = "Bosch-" + UUID.randomUUID().toString().substring(0, 8);
        BrandCreateRequest request = new BrandCreateRequest(brandName, "OEM Automotive Components");

        String responseBody = mockMvc.perform(post("/api/v1/product-brands")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid", notNullValue()))
                .andExpect(jsonPath("$.name", is(brandName)))
                .andExpect(jsonPath("$.description", is("OEM Automotive Components")))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the returned UUID matches the actual database UUID
        String returnedUuidStr = objectMapper.readTree(responseBody).get("uuid").asText();
        UUID returnedUuid = UUID.fromString(returnedUuidStr);

        UUID dbUuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM product_brands WHERE name = ?",
                UUID.class,
                brandName
        );
        assertEquals(dbUuid, returnedUuid, "Returned UUID must match database UUID exactly");
    }

    @Test
    @DisplayName("2. Brand listing returns UUID for every brand (both /product-brands and /brands endpoints)")
    void testBrandListingReturnsUuidForEveryBrand() throws Exception {
        String b1 = "ListingBrand1-" + UUID.randomUUID().toString().substring(0, 8);
        String b2 = "ListingBrand2-" + UUID.randomUUID().toString().substring(0, 8);

        createBrandDirectly(b1, "Desc 1");
        createBrandDirectly(b2, "Desc 2");

        // Test /api/v1/product-brands
        mockMvc.perform(get("/api/v1/product-brands")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].uuid", everyItem(notNullValue())));

        // Test /api/v1/brands
        mockMvc.perform(get("/api/v1/brands")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].uuid", everyItem(notNullValue())));

        // Test paginated /api/v1/product-brands/paged
        mockMvc.perform(get("/api/v1/product-brands/paged")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[*].uuid", everyItem(notNullValue())));
    }

    @Test
    @DisplayName("3. Brand retrieval by UUID returns the correct brand and UUID")
    void testBrandRetrievalReturnsCorrectUuid() throws Exception {
        String brandName = "Brembo-" + UUID.randomUUID().toString().substring(0, 8);
        UUID brandUuid = createBrandDirectly(brandName, "Brake Systems");

        mockMvc.perform(get("/api/v1/brands/" + brandUuid)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is(brandUuid.toString())))
                .andExpect(jsonPath("$.name", is(brandName)))
                .andExpect(jsonPath("$.description", is("Brake Systems")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("4. Brand update using UUID succeeds and returns updated data with preserved UUID")
    void testBrandUpdateUsingUuidSucceeds() throws Exception {
        String brandName = "Denso-" + UUID.randomUUID().toString().substring(0, 8);
        UUID brandUuid = createBrandDirectly(brandName, "Initial Description");

        BrandUpdateRequest updateRequest = new BrandUpdateRequest(brandName + "-Updated", "Updated Description");

        mockMvc.perform(put("/api/v1/brands/" + brandUuid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is(brandUuid.toString())))
                .andExpect(jsonPath("$.name", is(brandName + "-Updated")))
                .andExpect(jsonPath("$.description", is("Updated Description")));
    }

    @Test
    @DisplayName("5. Brand deactivation using UUID succeeds and sets active=false")
    void testBrandDeactivationUsingUuidSucceeds() throws Exception {
        String brandName = "Valeo-" + UUID.randomUUID().toString().substring(0, 8);
        UUID brandUuid = createBrandDirectly(brandName, "Electronics");

        mockMvc.perform(patch("/api/v1/brands/" + brandUuid + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Boolean activeStatus = jdbcTemplate.queryForObject(
                "SELECT active FROM product_brands WHERE uuid = ?",
                Boolean.class,
                brandUuid
        );
        assertFalse(activeStatus, "Brand must be marked inactive in database");
    }

    @Test
    @DisplayName("6. Unknown UUID returns 404 Not Found")
    void testUnknownUuidReturns404() throws Exception {
        UUID unknownUuid = UUID.randomUUID();

        // Get by unknown UUID -> 404
        mockMvc.perform(get("/api/v1/brands/" + unknownUuid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // Update unknown UUID -> 404
        mockMvc.perform(put("/api/v1/brands/" + unknownUuid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandUpdateRequest("Name", "Desc"))))
                .andExpect(status().isNotFound());

        // Deactivate unknown UUID -> 404
        mockMvc.perform(patch("/api/v1/brands/" + unknownUuid + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. Unauthorized brand update and deactivation return 403 Forbidden")
    void testUnauthorizedBrandModificationReturns403() throws Exception {
        String brandName = "AuthTest-" + UUID.randomUUID().toString().substring(0, 8);
        UUID brandUuid = createBrandDirectly(brandName, "Auth Test");

        // Cashier cannot update brand (requires BRANDS.UPDATE / AdminOrManager)
        mockMvc.perform(put("/api/v1/brands/" + brandUuid)
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BrandUpdateRequest("NewName", "NewDesc"))))
                .andExpect(status().isForbidden());

        // Cashier cannot deactivate brand (requires BRANDS.DEACTIVATE / AdminOnly)
        mockMvc.perform(patch("/api/v1/brands/" + brandUuid + "/deactivate")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private UUID createBrandDirectly(String name, String description) {
        UUID uuid = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product_brands (uuid, name, description, active) VALUES (?, ?, ?, TRUE)",
                uuid, name, description
        );
        return uuid;
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
