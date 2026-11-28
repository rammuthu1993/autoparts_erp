package com.autoparts.erp.tax;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingCloseRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingSupersedeRequest;
import com.autoparts.erp.tax.dto.TaxRateCloseRequest;
import com.autoparts.erp.tax.dto.TaxRateCreateRequest;
import com.autoparts.erp.tax.dto.TaxRateSupersedeRequest;
import com.autoparts.erp.tax.dto.TaxRateUpdateRequest;
import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("Tax Setup, Effective-Date Superseding & Historical Lookup Integration Tests")
class TaxSetupIntegrationTest {

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
    private ProductTaxMappingRepository mappingRepository;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
            ALTER TABLE tax_rates
                ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500);

            ALTER TABLE product_tax_mapping
                ADD COLUMN IF NOT EXISTS hsn_description VARCHAR(255),
                ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500),
                ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
                ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('TAX_RATES.VIEW', 'View Tax Rates', 'View GST rates and product tax mappings', 'TAX_RATES', 'VIEW'),
                ('TAX_RATES.CREATE', 'Create Tax Rates', 'Create new GST tax rates', 'TAX_RATES', 'CREATE'),
                ('TAX_RATES.UPDATE', 'Update Tax Rates', 'Edit tax rates and product tax mappings', 'TAX_RATES', 'UPDATE'),
                ('PRODUCT_TAX_MAPPINGS.MANAGE', 'Manage Product Tax Mappings', 'Assign, close, supersede, and bulk-assign product GST mappings', 'TAX_RATES', 'MAPPING_MANAGE')
            ON CONFLICT (code) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'MANAGER' AND p.code IN ('TAX_RATES.VIEW', 'PRODUCT_TAX_MAPPINGS.MANAGE')
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'CASHIER' AND p.code = 'TAX_RATES.VIEW'
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        String adminUser = "adm_tax_" + UUID.randomUUID().toString().substring(0, 8);
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("StrongPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));
        adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        String managerUser = "mgr_tax_" + UUID.randomUUID().toString().substring(0, 8);
        Long managerId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("StrongPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(managerId, Set.of("MANAGER"));
        managerToken = createBearerToken(managerUser, List.of("MANAGER"), managerId);

        String cashierUser = "csh_tax_" + UUID.randomUUID().toString().substring(0, 8);
        Long cashierId = userManagementRepository.create(cashierUser, cashierUser + "@example.com", passwordEncoder.encode("StrongPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));
        cashierToken = createBearerToken(cashierUser, List.of("CASHIER"), cashierId);
    }

    @Test
    @DisplayName("Tax rate lifecycle: creation, validation, close, supersede, summary, selectable, and RBAC")
    void testTaxRateLifecycleCloseAndSupersede() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String rateName = "GST_TEST_" + suffix;

        // 1. MANAGER cannot create tax rates (403)
        TaxRateCreateRequest createReq = new TaxRateCreateRequest(
                rateName, new BigDecimal("18.00"), new BigDecimal("9.00"), new BigDecimal("9.00"), new BigDecimal("18.00"),
                LocalDate.of(2026, 4, 1), null
        );
        mockMvc.perform(post("/api/v1/tax-rates")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // 2. Invalid GST split rejected (400)
        TaxRateCreateRequest invalidSplit = new TaxRateCreateRequest(
                rateName, new BigDecimal("18.00"), new BigDecimal("5.00"), new BigDecimal("5.00"), new BigDecimal("18.00"),
                LocalDate.of(2026, 4, 1), null
        );
        mockMvc.perform(post("/api/v1/tax-rates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSplit)))
                .andExpect(status().isBadRequest());

        // 3. ADMIN creates open-ended rate
        String createdJson = mockMvc.perform(post("/api/v1/tax-rates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxName").value(rateName))
                .andReturn().getResponse().getContentAsString();
        UUID rateUuid = UUID.fromString(objectMapper.readTree(createdJson).get("uuid").asText());

        // 4. Overlapping rate with same name rejected (409)
        TaxRateCreateRequest overlapReq = new TaxRateCreateRequest(
                rateName, new BigDecimal("12.00"), new BigDecimal("6.00"), new BigDecimal("6.00"), new BigDecimal("12.00"),
                LocalDate.of(2026, 10, 1), null
        );
        mockMvc.perform(post("/api/v1/tax-rates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlapReq)))
                .andExpect(status().isConflict());

        // 5. Supersede rate atomically at 2027-04-01
        TaxRateSupersedeRequest supersedeReq = new TaxRateSupersedeRequest(
                rateName, new BigDecimal("12.00"), new BigDecimal("6.00"), new BigDecimal("6.00"), new BigDecimal("12.00"),
                LocalDate.of(2027, 4, 1), null, "Budget 2027 slab revision"
        );
        String supersededJson = mockMvc.perform(post("/api/v1/tax-rates/" + rateUuid + "/supersede")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supersedeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gstRate").value(12.0))
                .andExpect(jsonPath("$.effectiveFrom").value("2027-04-01"))
                .andReturn().getResponse().getContentAsString();
        UUID replacementUuid = UUID.fromString(objectMapper.readTree(supersededJson).get("uuid").asText());

        // Original rate should now have effectiveTo = 2027-04-01
        mockMvc.perform(get("/api/v1/tax-rates/" + rateUuid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2027-04-01"));

        // 6. Close replacement rate at 2028-04-01
        mockMvc.perform(post("/api/v1/tax-rates/" + replacementUuid + "/close")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaxRateCloseRequest(LocalDate.of(2028, 4, 1), "Sunset"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2028-04-01"));

        // 7. Selectable & Summary endpoints
        mockMvc.perform(get("/api/v1/tax-rates/selectable")
                        .param("effectiveDate", "2027-06-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid=='" + replacementUuid + "')]", hasSize(1)));

        mockMvc.perform(get("/api/v1/tax-rates/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRatesCount").isNumber());
    }

    @Test
    @DisplayName("Product tax mapping: supersede, close, paginated history, bulk assign, and historical lookup after deactivation")
    void testProductMappingSupersedeBulkAndHistoricalLookup() throws Exception {
        UUID prod1 = createTestProduct("TP1-" + UUID.randomUUID().toString().substring(0, 6));
        UUID prod2 = createTestProduct("TP2-" + UUID.randomUUID().toString().substring(0, 6));

        // Create two rates: historical 18% (2025-01-01..null) and 12% (2025-01-01..null)
        UUID rate18Uuid = createTestRate("HIST18_" + UUID.randomUUID().toString().substring(0, 6), "18.00", "9.00", LocalDate.of(2025, 1, 1));
        UUID rate12Uuid = createTestRate("NEW12_" + UUID.randomUUID().toString().substring(0, 6), "12.00", "6.00", LocalDate.of(2025, 1, 1));

        // 1. CASHIER cannot create mapping (403)
        ProductTaxMappingCreateRequest mReq = new ProductTaxMappingCreateRequest(
                rate18Uuid, "87083000", "Brake pads and linings", LocalDate.of(2025, 4, 1), null, "Initial HSN"
        );
        mockMvc.perform(post("/api/v1/products/" + prod1 + "/tax-mappings")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isForbidden());

        // 2. MANAGER creates initial open-ended mapping
        String mapJson = mockMvc.perform(post("/api/v1/products/" + prod1 + "/tax-mappings")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hsnCode").value("87083000"))
                .andExpect(jsonPath("$.hsnDescription").value("Brake pads and linings"))
                .andReturn().getResponse().getContentAsString();
        UUID mapping1Uuid = UUID.fromString(objectMapper.readTree(mapJson).get("uuid").asText());

        // 3. Supersede mapping at 2027-04-01
        ProductTaxMappingSupersedeRequest supReq = new ProductTaxMappingSupersedeRequest(
                rate12Uuid, "87083000", "Brake pads revised", LocalDate.of(2027, 4, 1), null, "GST slab reduction"
        );
        String map2Json = mockMvc.perform(post("/api/v1/products/" + prod1 + "/tax-mappings/" + mapping1Uuid + "/supersede")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveFrom").value("2027-04-01"))
                .andExpect(jsonPath("$.gstRate").value(12.0))
                .andReturn().getResponse().getContentAsString();
        UUID mapping2Uuid = UUID.fromString(objectMapper.readTree(map2Json).get("uuid").asText());

        // Verify original mapping was closed at 2027-04-01 and accessible by UUID
        mockMvc.perform(get("/api/v1/product-tax-mappings/" + mapping1Uuid)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2027-04-01"));

        // Verify paginated history returns both records
        mockMvc.perform(get("/api/v1/products/" + prod1 + "/tax-mappings/paged")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 4. Close replacement mapping at 2028-04-01
        mockMvc.perform(post("/api/v1/products/" + prod1 + "/tax-mappings/" + mapping2Uuid + "/close")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductTaxMappingCloseRequest(LocalDate.of(2028, 4, 1), "End of schedule"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTo").value("2028-04-01"));

        // 5. Deactivate rate18Uuid and verify historical lookup for 2025-06-15 STILL resolves it!
        mockMvc.perform(put("/api/v1/tax-rates/" + rate18Uuid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaxRateUpdateRequest("HIST18_OLD_" + UUID.randomUUID().toString().substring(0, 6), false, "Retired"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        Optional<ProductTaxMapping> historical = mappingRepository.findMappingByProductUuidAndDate(prod1, LocalDate.of(2025, 6, 15));
        assertTrue(historical.isPresent(), "Historical lookup must still resolve valid mapping after rate deactivation");
        assertEquals(rate18Uuid, historical.get().taxRateUuid());

        // 6. Bulk assign 12% rate to prod1 (which already has future mapping2 at 2027-04-01) and prod2 (unmapped)
        BulkProductTaxAssignRequest bulkReq = new BulkProductTaxAssignRequest(
                List.of(prod1, prod2), rate12Uuid, "87089900", "Auto parts", LocalDate.of(2026, 6, 1), null, true, "Bulk update"
        );
        mockMvc.perform(post("/api/v1/product-tax-mappings/bulk-assign")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }

    private UUID createTestRate(String name, String gst, String half, LocalDate from) throws Exception {
        TaxRateCreateRequest req = new TaxRateCreateRequest(
                name, new BigDecimal(gst), new BigDecimal(half), new BigDecimal(half), new BigDecimal(gst), from, null
        );
        String json = mockMvc.perform(post("/api/v1/tax-rates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("uuid").asText());
    }

    private UUID createTestProduct(String code) {
        Long catId = jdbcTemplate.queryForObject("SELECT id FROM product_categories WHERE active = TRUE LIMIT 1", Long.class);
        Long brandId = jdbcTemplate.queryForObject("SELECT id FROM product_brands WHERE active = TRUE LIMIT 1", Long.class);
        if (catId == null) {
            catId = jdbcTemplate.queryForObject(
                "INSERT INTO product_categories (uuid, name, active) VALUES (gen_random_uuid(), 'TaxTestCat_' || substr(gen_random_uuid()::text, 1, 6), TRUE) RETURNING id",
                Long.class
            );
        }
        if (brandId == null) {
            brandId = jdbcTemplate.queryForObject(
                "INSERT INTO product_brands (uuid, name, active) VALUES (gen_random_uuid(), 'TaxTestBrand_' || substr(gen_random_uuid()::text, 1, 6), TRUE) RETURNING id",
                Long.class
            );
        }
        UUID uuid = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO products (uuid, product_code, part_no, name, category_id, brand_id, purchase_price, selling_price, minimum_stock, unit, barcode, barcode_type, active) " +
            "VALUES (?, ?, ?, ?, ?, ?, 100.00, 150.00, 1, 'PCS', ?, 'CODE_128', TRUE)",
            uuid, code, "PART-" + code, "Product " + code, catId, brandId, "BC-" + code
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
