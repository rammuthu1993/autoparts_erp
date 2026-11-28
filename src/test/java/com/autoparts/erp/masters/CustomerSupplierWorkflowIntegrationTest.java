package com.autoparts.erp.masters;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.customer.dto.CustomerCreateRequest;
import com.autoparts.erp.customer.dto.CustomerUpdateRequest;
import com.autoparts.erp.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.supplier.dto.SupplierUpdateRequest;
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
import java.math.BigDecimal;
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
@DisplayName("Customer & Supplier Master Workflow Integration Tests")
class CustomerSupplierWorkflowIntegrationTest {

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
            ALTER TABLE customers
                ADD COLUMN IF NOT EXISTS customer_type VARCHAR(30) NOT NULL DEFAULT 'RETAIL',
                ADD COLUMN IF NOT EXISTS contact_person VARCHAR(150),
                ADD COLUMN IF NOT EXISTS alternate_phone VARCHAR(20),
                ADD COLUMN IF NOT EXISTS gst_registration_type VARCHAR(30),
                ADD COLUMN IF NOT EXISTS shipping_address VARCHAR(500),
                ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(12, 2),
                ADD COLUMN IF NOT EXISTS credit_period_days INTEGER;

            CREATE SEQUENCE IF NOT EXISTS supplier_code_seq START WITH 1 INCREMENT BY 1;

            ALTER TABLE suppliers
                ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(50),
                ADD COLUMN IF NOT EXISTS supplier_type VARCHAR(30) NOT NULL DEFAULT 'DISTRIBUTOR',
                ADD COLUMN IF NOT EXISTS alternate_phone VARCHAR(20),
                ADD COLUMN IF NOT EXISTS gst_registration_type VARCHAR(30),
                ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(12, 2);

            UPDATE suppliers
            SET supplier_code = 'SUP' || LPAD(id::TEXT, 6, '0')
            WHERE supplier_code IS NULL;

            CREATE TABLE IF NOT EXISTS sales_invoice_cancellations (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                uuid UUID NOT NULL DEFAULT gen_random_uuid(),
                sale_id BIGINT NOT NULL REFERENCES sales(id),
                cancellation_number VARCHAR(50),
                cancelled_invoice_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
                cancelled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

            ALTER TABLE sales_invoice_cancellations
                ADD COLUMN IF NOT EXISTS uuid UUID NOT NULL DEFAULT gen_random_uuid(),
                ADD COLUMN IF NOT EXISTS cancellation_number VARCHAR(50);

            CREATE TABLE IF NOT EXISTS sales_cancellation_refunds (
                id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                uuid UUID NOT NULL DEFAULT gen_random_uuid(),
                sale_id BIGINT NOT NULL REFERENCES sales(id),
                refund_number VARCHAR(50),
                amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
                status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
                refund_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

            ALTER TABLE sales_cancellation_refunds
                ADD COLUMN IF NOT EXISTS uuid UUID NOT NULL DEFAULT gen_random_uuid(),
                ADD COLUMN IF NOT EXISTS refund_number VARCHAR(50),
                ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);

            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('CUSTOMERS.VIEW', 'View Customers', 'View customers', 'CUSTOMERS', 'VIEW'),
                ('CUSTOMERS.CREATE', 'Create Customers', 'Create customers', 'CUSTOMERS', 'CREATE'),
                ('CUSTOMERS.UPDATE', 'Update Customers', 'Update customers', 'CUSTOMERS', 'UPDATE'),
                ('CUSTOMERS.DEACTIVATE', 'Deactivate Customers', 'Deactivate customers', 'CUSTOMERS', 'DEACTIVATE'),
                ('CUSTOMERS.LEDGER_VIEW', 'View Customer Ledger', 'View customer ledger', 'CUSTOMERS', 'LEDGER_VIEW'),
                ('SUPPLIERS.VIEW', 'View Suppliers', 'View suppliers', 'SUPPLIERS', 'VIEW'),
                ('SUPPLIERS.CREATE', 'Create Suppliers', 'Create suppliers', 'SUPPLIERS', 'CREATE'),
                ('SUPPLIERS.UPDATE', 'Update Suppliers', 'Update suppliers', 'SUPPLIERS', 'UPDATE'),
                ('SUPPLIERS.DEACTIVATE', 'Deactivate Suppliers', 'Deactivate suppliers', 'SUPPLIERS', 'DEACTIVATE'),
                ('SUPPLIERS.LEDGER_VIEW', 'View Supplier Ledger', 'View supplier ledger', 'SUPPLIERS', 'LEDGER_VIEW')
            ON CONFLICT (code) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name IN ('ADMIN', 'MANAGER')
              AND (p.code LIKE 'CUSTOMERS.%' OR p.code LIKE 'SUPPLIERS.%')
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'CASHIER'
              AND p.code IN ('CUSTOMERS.VIEW', 'CUSTOMERS.LEDGER_VIEW', 'SUPPLIERS.VIEW')
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        String adminUser = "cs_adm_" + UUID.randomUUID().toString().substring(0, 8);
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("TestPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));
        adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        String managerUser = "cs_mgr_" + UUID.randomUUID().toString().substring(0, 8);
        Long managerId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("TestPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(managerId, Set.of("MANAGER"));
        managerToken = createBearerToken(managerUser, List.of("MANAGER"), managerId);

        String cashierUser = "cs_csh_" + UUID.randomUUID().toString().substring(0, 8);
        Long cashierId = userManagementRepository.create(cashierUser, cashierUser + "@example.com", passwordEncoder.encode("TestPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));
        cashierToken = createBearerToken(cashierUser, List.of("CASHIER"), cashierId);
    }

    @Test
    @DisplayName("Customer lifecycle: blank optional fields, duplicate warning, GSTIN 409, email/GSTIN search, summary, ledger, manager deactivate & reactivate")
    void testCustomerFullLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String gstin = "29ABCDE" + String.format("%04d", Math.abs(suffix.hashCode()) % 10000) + "F1Z5";

        // 1. Blank optional fields accepted
        CustomerCreateRequest blankOptReq = new CustomerCreateRequest(
                "BlankOpt Customer " + suffix, "", "", "", "Chennai", "Tamil Nadu", "", ""
        );
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankOptReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerCode", startsWith("CUS")));

        // 2. Full customer create with GSTIN & commercial terms
        CustomerCreateRequest fullReq = new CustomerCreateRequest(
                "Guhan Fleet " + suffix, "9876543210", "fleet." + suffix.toLowerCase() + "@test.com",
                "12 Anna Salai", "Chennai", "Tamil Nadu", "600002", gstin,
                "CORPORATE", "Karthik", "9876543211", "REGULAR", "12 Anna Salai Yard",
                new BigDecimal("50000.00"), 30
        );
        String createdJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerType", is("CORPORATE")))
                .andExpect(jsonPath("$.gstin", is(gstin)))
                .andReturn().getResponse().getContentAsString();
        UUID customerUuid = UUID.fromString(objectMapper.readTree(createdJson).get("uuid").asText());

        // 3. Duplicate warning endpoint detects shared phone/email
        mockMvc.perform(get("/api/v1/customers/check-duplicates")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("phone", "9876543210"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uuid == '" + customerUuid + "')].matchedFields[0]", hasItem("phone")));

        // 4. Duplicate GSTIN rejected -> 409
        CustomerCreateRequest dupGstinReq = new CustomerCreateRequest(
                "Another Customer " + suffix, "9123456789", null, null, null, null, null, gstin
        );
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupGstinReq)))
                .andExpect(status().isConflict());

        // 5. Search by email & filter by city/customerType
        mockMvc.perform(get("/api/v1/customers/paged")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("search", "fleet." + suffix.toLowerCase())
                        .param("city", "Chennai")
                        .param("customerType", "CORPORATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.uuid == '" + customerUuid + "')]", hasSize(1)));

        // 6. Summary & Ledger endpoints
        mockMvc.perform(get("/api/v1/customers/" + customerUuid + "/summary")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingReceivable", is(0)));

        mockMvc.perform(get("/api/v1/customers/" + customerUuid + "/ledger")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.customerUuid", is(customerUuid.toString())));

        // 7. Manager deactivate -> inactive still viewable in details/summary -> cashier forbidden on reactivate -> manager reactivate
        mockMvc.perform(patch("/api/v1/customers/" + customerUuid + "/deactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/customers/" + customerUuid)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        mockMvc.perform(patch("/api/v1/customers/" + customerUuid + "/reactivate")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/customers/" + customerUuid + "/reactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));

        // 8. Update customer
        mockMvc.perform(put("/api/v1/customers/" + customerUuid)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CustomerUpdateRequest("Guhan Fleet Updated " + suffix, "9876543210",
                                        "fleet." + suffix.toLowerCase() + "@test.com", "12 Anna Salai", "Chennai", "Tamil Nadu", "600002", gstin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName", is("Guhan Fleet Updated " + suffix)));
    }

    @Test
    @DisplayName("Supplier lifecycle: generated code, GSTIN validation, update duplicate 409, email search, summary, ledger, manager deactivate & reactivate")
    void testSupplierFullLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String phone1 = "98" + String.format("%08d", Math.abs((suffix + "1").hashCode()) % 100000000);
        String phone2 = "97" + String.format("%08d", Math.abs((suffix + "2").hashCode()) % 100000000);
        String gstin1 = "33ABCDE" + String.format("%04d", Math.abs((suffix + "1").hashCode()) % 10000) + "G1Z2";

        SupplierCreateRequest req1 = new SupplierCreateRequest(
                "TVS Spares " + suffix, "Ramesh", phone1, "tvs." + suffix.toLowerCase() + "@test.com",
                "Industrial Estate", "Hosur", "Tamil Nadu", "635109", gstin1, 30,
                "MANUFACTURER", null, "REGULAR", new BigDecimal("200000.00")
        );
        String created1 = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierCode", startsWith("SUP")))
                .andExpect(jsonPath("$.supplierType", is("MANUFACTURER")))
                .andReturn().getResponse().getContentAsString();
        UUID sup1Uuid = UUID.fromString(objectMapper.readTree(created1).get("uuid").asText());

        // Create second supplier and verify update-time duplicate phone -> 409
        SupplierCreateRequest req2 = new SupplierCreateRequest(
                "Lucas Parts " + suffix, "Suresh", phone2, "lucas." + suffix.toLowerCase() + "@test.com",
                "Padi", "Chennai", "Tamil Nadu", "600050", "", 15
        );
        String created2 = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID sup2Uuid = UUID.fromString(objectMapper.readTree(created2).get("uuid").asText());

        mockMvc.perform(put("/api/v1/suppliers/" + sup2Uuid)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SupplierUpdateRequest("Lucas Parts " + suffix, "Suresh", phone1,
                                        "lucas." + suffix.toLowerCase() + "@test.com", "Padi", "Chennai", "Tamil Nadu", "600050", "", 15))))
                .andExpect(status().isConflict());

        // Search by email
        mockMvc.perform(get("/api/v1/suppliers/paged")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("search", "tvs." + suffix.toLowerCase())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.uuid == '" + sup1Uuid + "')]", hasSize(1)));

        // Summary & Ledger
        mockMvc.perform(get("/api/v1/suppliers/" + sup1Uuid + "/summary")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingPayable", is(0)));

        mockMvc.perform(get("/api/v1/suppliers/" + sup1Uuid + "/ledger")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.supplierUuid", is(sup1Uuid.toString())));

        // Manager deactivate & reactivate
        mockMvc.perform(patch("/api/v1/suppliers/" + sup1Uuid + "/deactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/suppliers/" + sup1Uuid + "/reactivate")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));
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
