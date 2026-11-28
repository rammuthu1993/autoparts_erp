package com.autoparts.erp.audit;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.settings.dto.AuditRetentionSettingsDto;
import com.autoparts.erp.settings.dto.BusinessProfileSettingsDto;
import com.autoparts.erp.settings.dto.FinancialYearSettingsDto;
import com.autoparts.erp.settings.dto.InvoiceSettingsDto;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("Audit Logs & Domain Settings Integration Tests")
class AuditSettingsIntegrationTest {

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
    private AuditLogService auditLogService;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mockMvc;
    private String adminToken;
    private String managerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_audit_logs_username_lower ON audit_logs (LOWER(username));
            CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_id ON audit_logs (entity_id);

            CREATE TABLE IF NOT EXISTS business_profile_settings (
                id BIGSERIAL PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
                business_name VARCHAR(200) NOT NULL,
                legal_name VARCHAR(200),
                gstin VARCHAR(15),
                pan VARCHAR(10),
                registered_address TEXT,
                shop_address TEXT,
                city VARCHAR(100),
                state VARCHAR(100),
                state_code VARCHAR(10),
                pincode VARCHAR(12),
                phone VARCHAR(30),
                email VARCHAR(150),
                logo_url VARCHAR(500),
                currency VARCHAR(10) NOT NULL DEFAULT 'INR',
                time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
                updated_by VARCHAR(100),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS invoice_document_settings (
                id BIGSERIAL PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
                invoice_prefix VARCHAR(20) NOT NULL DEFAULT 'INV',
                number_padding INT NOT NULL DEFAULT 5,
                fy_reset_policy VARCHAR(20) NOT NULL DEFAULT 'YEARLY',
                print_page_size VARCHAR(20) NOT NULL DEFAULT 'A4',
                copy_count INT NOT NULL DEFAULT 2,
                show_gst_breakdown BOOLEAN NOT NULL DEFAULT TRUE,
                show_hsn_sac BOOLEAN NOT NULL DEFAULT TRUE,
                terms_and_conditions TEXT,
                footer_text VARCHAR(500),
                updated_by VARCHAR(100),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS financial_year_settings (
                id BIGSERIAL PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
                fy_label VARCHAR(20) NOT NULL,
                start_date DATE NOT NULL,
                end_date DATE NOT NULL,
                period_lock_date DATE,
                prevent_locked_period_edits BOOLEAN NOT NULL DEFAULT TRUE,
                last_reopen_reason VARCHAR(500),
                is_current BOOLEAN NOT NULL DEFAULT TRUE,
                updated_by VARCHAR(100),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS audit_retention_settings (
                id BIGSERIAL PRIMARY KEY,
                uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
                retention_days INT NOT NULL DEFAULT 365,
                archive_frequency VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
                archive_destination VARCHAR(300),
                last_archive_at TIMESTAMPTZ,
                last_archive_status VARCHAR(30),
                last_archive_error TEXT,
                updated_by VARCHAR(100),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );

            INSERT INTO business_profile_settings (business_name, currency, time_zone)
            SELECT 'AutoParts ERP', 'INR', 'Asia/Kolkata'
            WHERE NOT EXISTS (SELECT 1 FROM business_profile_settings);

            INSERT INTO invoice_document_settings (invoice_prefix, number_padding, fy_reset_policy, print_page_size, copy_count)
            SELECT 'INV', 5, 'ANNUAL_FY', 'A4', 2
            WHERE NOT EXISTS (SELECT 1 FROM invoice_document_settings);

            INSERT INTO financial_year_settings (fy_label, start_date, end_date, prevent_locked_period_edits, is_current)
            SELECT 'FY 2026-27', DATE '2026-04-01', DATE '2027-03-31', TRUE, TRUE
            WHERE NOT EXISTS (SELECT 1 FROM financial_year_settings);

            INSERT INTO audit_retention_settings (retention_days, archive_frequency)
            SELECT 365, 'MANUAL'
            WHERE NOT EXISTS (SELECT 1 FROM audit_retention_settings);

            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('AUDIT_LOGS.VIEW', 'View Audit Logs', 'View audit logs', 'AUDIT_LOGS', 'VIEW'),
                ('SETTINGS.BUSINESS_PROFILE.VIEW', 'View Business Profile Settings', 'View business profile', 'SETTINGS', 'BUSINESS_PROFILE_VIEW'),
                ('SETTINGS.BUSINESS_PROFILE.UPDATE', 'Update Business Profile Settings', 'Update business profile', 'SETTINGS', 'BUSINESS_PROFILE_UPDATE'),
                ('SETTINGS.INVOICE.VIEW', 'View Invoice Settings', 'View invoice settings', 'SETTINGS', 'INVOICE_VIEW'),
                ('SETTINGS.INVOICE.UPDATE', 'Update Invoice Settings', 'Update invoice settings', 'SETTINGS', 'INVOICE_UPDATE'),
                ('SETTINGS.FINANCIAL_YEAR.VIEW', 'View Financial Year Settings', 'View financial year settings', 'SETTINGS', 'FINANCIAL_YEAR_VIEW'),
                ('SETTINGS.FINANCIAL_YEAR.UPDATE', 'Update Financial Year Settings', 'Update financial year settings', 'SETTINGS', 'FINANCIAL_YEAR_UPDATE'),
                ('SETTINGS.AUDIT_RETENTION.VIEW', 'View Audit Retention Settings', 'View audit retention settings', 'SETTINGS', 'AUDIT_RETENTION_VIEW'),
                ('SETTINGS.AUDIT_RETENTION.UPDATE', 'Update Audit Retention Settings', 'Update audit retention settings', 'SETTINGS', 'AUDIT_RETENTION_UPDATE'),
                ('SETTINGS.SECURITY_POLICY.VIEW', 'View Security Policy', 'View security policy', 'SETTINGS', 'SECURITY_POLICY_VIEW'),
                ('SETTINGS.RUNTIME.VIEW', 'View Runtime Status', 'View runtime status', 'SETTINGS', 'RUNTIME_VIEW')
            ON CONFLICT (code) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        String adminUser = "adm_aud_" + UUID.randomUUID().toString().substring(0, 8);
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("StrongPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));
        adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        String managerUser = "mgr_aud_" + UUID.randomUUID().toString().substring(0, 8);
        Long managerId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("StrongPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(managerId, Set.of("MANAGER"));
        managerToken = createBearerToken(managerUser, List.of("MANAGER"), managerId);
    }

    @Test
    @DisplayName("Audit metadata, entityId/date-range validation, and sensitive field redaction")
    void testAuditMetadataFilteringAndSanitization() throws Exception {
        String entityId = "ENT-" + UUID.randomUUID().toString().substring(0, 8);
        auditLogService.log(
                "CREATE", "TEST_ENTITY", entityId,
                "Sensitive payload test",
                null,
                Map.of(
                        "username", "alice",
                        "password", "SuperSecret123",
                        "nested", Map.of("refreshToken", "tok-999", "apiKey", "key-abc")
                )
        );

        // 1. Metadata endpoint returns real actions (no LOGIN) and entityTypes
        mockMvc.perform(get("/api/v1/admin/audit-logs/metadata")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions", hasItem("CREATE")))
                .andExpect(jsonPath("$.actions", not(hasItem("LOGIN"))))
                .andExpect(jsonPath("$.entityTypes", hasItem("TEST_ENTITY")));

        // 2. Entity ID filter + sanitized JSON payload
        mockMvc.perform(get("/api/v1/admin/audit-logs/paged")
                        .param("entityId", entityId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].entityId").value(entityId))
                .andExpect(jsonPath("$.content[0].newValues", containsString("[REDACTED]")))
                .andExpect(jsonPath("$.content[0].newValues", not(containsString("SuperSecret123"))))
                .andExpect(jsonPath("$.content[0].newValues", not(containsString("tok-999"))))
                .andExpect(jsonPath("$.content[0].newValues", not(containsString("key-abc"))));

        // 3. Invalid date range rejected with 400
        mockMvc.perform(get("/api/v1/admin/audit-logs/paged")
                        .param("fromDate", "2026-12-10")
                        .param("toDate", "2026-12-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Domain settings CRUD, Financial Year reopen reason enforcement, read-only endpoints, and RBAC")
    void testDomainSettingsWorkflow() throws Exception {
        // 1. Non-admin (MANAGER) denied access
        mockMvc.perform(get("/api/v1/admin/settings/business-profile")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // 2. Business profile GET + PUT + audit log verification
        BusinessProfileSettingsDto bpUpdate = new BusinessProfileSettingsDto(
                null, "Precision Auto Parts", "Precision Auto Parts Pvt Ltd", "29ABCDE1234F1Z5", "ABCDE1234F",
                "12 Industrial Estate", "12 Industrial Estate", "Bengaluru", "Karnataka", "29", "560001",
                "9876543210", "billing@precision.example.com", null, "INR", "Asia/Kolkata", null
        );
        mockMvc.perform(put("/api/v1/admin/settings/business-profile")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bpUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Precision Auto Parts"))
                .andExpect(jsonPath("$.gstin").value("29ABCDE1234F1Z5"));

        // 3. Invoice settings PUT
        InvoiceSettingsDto invUpdate = new InvoiceSettingsDto(
                null, "AP-", 6, "ANNUAL_FY", "A4", 2, true, true, "Goods once sold cannot be returned.", "Thank you", null
        );
        mockMvc.perform(put("/api/v1/admin/settings/invoice")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicePrefix").value("AP-"))
                .andExpect(jsonPath("$.numberPadding").value(6));

        // 4. Financial year lock + reopen reason enforcement
        FinancialYearSettingsDto lockReq = new FinancialYearSettingsDto(
                null, "FY 2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                LocalDate.of(2026, 10, 31), true, null, null, true, null
        );
        mockMvc.perform(put("/api/v1/admin/settings/financial-year")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodLockDate").value("2026-10-31"));

        // Moving periodLockDate earlier WITHOUT reopenReason must fail with 400
        FinancialYearSettingsDto reopenWithoutReason = new FinancialYearSettingsDto(
                null, "FY 2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                LocalDate.of(2026, 9, 30), true, "", null, true, null
        );
        mockMvc.perform(put("/api/v1/admin/settings/financial-year")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reopenWithoutReason)))
                .andExpect(status().isBadRequest());

        // Moving periodLockDate earlier WITH reopenReason must succeed
        FinancialYearSettingsDto reopenWithReason = new FinancialYearSettingsDto(
                null, "FY 2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                LocalDate.of(2026, 9, 30), true, "Year-end tax adjustment", null, true, null
        );
        mockMvc.perform(put("/api/v1/admin/settings/financial-year")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reopenWithReason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastReopenReason").value("Year-end tax adjustment"));

        // 5. Audit retention settings PUT
        AuditRetentionSettingsDto retUpdate = new AuditRetentionSettingsDto(
                null, 730, "MONTHLY", "gs://autoparts-audit-archive", null, null, null, null
        );
        mockMvc.perform(put("/api/v1/admin/settings/audit-retention")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").value(730));

        // 6. Read-only Security Policy & Runtime Status
        mockMvc.perform(get("/api/v1/admin/settings/security-policy")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.minPasswordLength").isNumber());

        mockMvc.perform(get("/api/v1/admin/settings/runtime-status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseConnected").value(true));
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
