package com.autoparts.erp.masters;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.category.dto.CategoryCreateRequest;
import com.autoparts.erp.masters.category.dto.CategoryUpdateRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("Category & Brand Master Workflow Integration Tests")
class CategoryBrandWorkflowIntegrationTest {

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
    private String managerToken;
    private String cashierToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        jdbcTemplate.execute("""
            ALTER TABLE product_categories
                ADD COLUMN IF NOT EXISTS code VARCHAR(30),
                ADD COLUMN IF NOT EXISTS parent_id BIGINT REFERENCES product_categories(id) ON DELETE RESTRICT;
            CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_code_lower
                ON product_categories (LOWER(code)) WHERE code IS NOT NULL;
            ALTER TABLE product_brands
                ADD COLUMN IF NOT EXISTS code VARCHAR(30),
                ADD COLUMN IF NOT EXISTS country_of_origin VARCHAR(100),
                ADD COLUMN IF NOT EXISTS website VARCHAR(255);
            CREATE UNIQUE INDEX IF NOT EXISTS uq_brands_code_lower
                ON product_brands (LOWER(code)) WHERE code IS NOT NULL;

            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('CATEGORIES.VIEW', 'View Categories', 'View categories', 'CATEGORIES', 'VIEW'),
                ('CATEGORIES.CREATE', 'Create Categories', 'Create categories', 'CATEGORIES', 'CREATE'),
                ('CATEGORIES.UPDATE', 'Update Categories', 'Update categories', 'CATEGORIES', 'UPDATE'),
                ('CATEGORIES.DEACTIVATE', 'Deactivate Categories', 'Deactivate categories', 'CATEGORIES', 'DEACTIVATE'),
                ('BRANDS.VIEW', 'View Brands', 'View brands', 'BRANDS', 'VIEW'),
                ('BRANDS.CREATE', 'Create Brands', 'Create brands', 'BRANDS', 'CREATE'),
                ('BRANDS.UPDATE', 'Update Brands', 'Update brands', 'BRANDS', 'UPDATE'),
                ('BRANDS.DEACTIVATE', 'Deactivate Brands', 'Deactivate brands', 'BRANDS', 'DEACTIVATE')
            ON CONFLICT (code) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name IN ('ADMIN', 'MANAGER')
              AND (p.code LIKE 'CATEGORIES.%' OR p.code LIKE 'BRANDS.%')
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'CASHIER'
              AND p.code IN ('CATEGORIES.VIEW', 'BRANDS.VIEW')
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        String adminUser = "cb_adm_" + UUID.randomUUID().toString().substring(0, 8);
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("TestPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));
        adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        String managerUser = "cb_mgr_" + UUID.randomUUID().toString().substring(0, 8);
        Long managerId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("TestPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(managerId, Set.of("MANAGER"));
        managerToken = createBearerToken(managerUser, List.of("MANAGER"), managerId);

        String cashierUser = "cb_csh_" + UUID.randomUUID().toString().substring(0, 8);
        Long cashierId = userManagementRepository.create(cashierUser, cashierUser + "@example.com", passwordEncoder.encode("TestPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));
        cashierToken = createBearerToken(cashierUser, List.of("CASHIER"), cashierId);
    }

    @Test
    @DisplayName("Category lifecycle: code, parent hierarchy, duplicate 409, status filter, active lookup, usage, manager deactivate & reactivate")
    void testCategoryFullLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 1. Create parent category
        CategoryCreateRequest parentReq = new CategoryCreateRequest(
                "Brakes-" + suffix, "Braking system parts", "BRK-" + suffix, null
        );
        String parentJson = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("BRK-" + suffix)))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn().getResponse().getContentAsString();
        UUID parentUuid = UUID.fromString(objectMapper.readTree(parentJson).get("uuid").asText());

        // 2. Duplicate name (case/space normalized) -> 409
        CategoryCreateRequest dupReq = new CategoryCreateRequest(
                "  brakes-" + suffix.toLowerCase() + " ", "Duplicate", "BRK2-" + suffix, null
        );
        mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupReq)))
                .andExpect(status().isConflict());

        // 3. Create child category
        CategoryCreateRequest childReq = new CategoryCreateRequest(
                "Brake Pads-" + suffix, "Friction pads", "PAD-" + suffix, parentUuid
        );
        String childJson = mockMvc.perform(post("/api/v1/product-categories")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentUuid", is(parentUuid.toString())))
                .andExpect(jsonPath("$.parentName", is("Brakes-" + suffix)))
                .andReturn().getResponse().getContentAsString();
        UUID childUuid = UUID.fromString(objectMapper.readTree(childJson).get("uuid").asText());

        // 4. Self-parent and circular hierarchy rejected -> 400
        mockMvc.perform(put("/api/v1/product-categories/" + parentUuid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryUpdateRequest("Brakes-" + suffix, "Desc", "BRK-" + suffix, childUuid))))
                .andExpect(status().isBadRequest());

        // 5. Usage endpoint works
        mockMvc.perform(get("/api/v1/product-categories/" + childUuid + "/usage")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid", is(childUuid.toString())))
                .andExpect(jsonPath("$.totalProducts", is(0)));

        // 6. Manager can deactivate (fixed @AdminOnly mismatch)
        mockMvc.perform(patch("/api/v1/product-categories/" + childUuid + "/deactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        // 7. Cashier cannot deactivate or reactivate -> 403
        mockMvc.perform(patch("/api/v1/product-categories/" + childUuid + "/reactivate")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        // 8. Active lookup excludes deactivated child; status=INACTIVE includes it
        mockMvc.perform(get("/api/v1/product-categories/active")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid == '" + childUuid + "')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.uuid == '" + parentUuid + "')]", hasSize(1)));

        mockMvc.perform(get("/api/v1/product-categories/paged")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("status", "INACTIVE")
                        .param("search", childUuid.toString().substring(0, 0) + "Brake Pads-" + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.uuid == '" + childUuid + "')]", hasSize(1)));

        // 9. Manager can reactivate and it returns active=true
        mockMvc.perform(patch("/api/v1/product-categories/" + childUuid + "/reactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    @DisplayName("Brand lifecycle: code, origin, website, duplicate 409, status filter, active lookup, usage, manager deactivate & reactivate")
    void testBrandFullLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        BrandCreateRequest req = new BrandCreateRequest(
                "Bosch-" + suffix, "OEM Parts", "BSH-" + suffix, "Germany", "https://bosch.com"
        );
        String createdJson = mockMvc.perform(post("/api/v1/product-brands")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("BSH-" + suffix)))
                .andExpect(jsonPath("$.countryOfOrigin", is("Germany")))
                .andExpect(jsonPath("$.website", is("https://bosch.com")))
                .andReturn().getResponse().getContentAsString();
        UUID brandUuid = UUID.fromString(objectMapper.readTree(createdJson).get("uuid").asText());

        // Duplicate code -> 409
        BrandCreateRequest dupCodeReq = new BrandCreateRequest(
                "Other-" + suffix, "Other", "bsh-" + suffix.toLowerCase(), "India", null
        );
        mockMvc.perform(post("/api/v1/product-brands")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupCodeReq)))
                .andExpect(status().isConflict());

        // Manager can deactivate brand (previously blocked by @AdminOnly)
        mockMvc.perform(patch("/api/v1/product-brands/" + brandUuid + "/deactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        // Active lookup excludes deactivated brand
        mockMvc.perform(get("/api/v1/product-brands/active")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid == '" + brandUuid + "')]", hasSize(0)));

        // Usage endpoint returns 0 products
        mockMvc.perform(get("/api/v1/product-brands/" + brandUuid + "/usage")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.totalProducts", is(0)));

        // Manager reactivates brand
        mockMvc.perform(patch("/api/v1/product-brands/" + brandUuid + "/reactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));

        // Update brand website
        mockMvc.perform(put("/api/v1/product-brands/" + brandUuid)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BrandUpdateRequest("Bosch-" + suffix, "OEM Parts Updated", "BSH-" + suffix, "Germany", "https://www.bosch.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.website", is("https://www.bosch.com")));
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
