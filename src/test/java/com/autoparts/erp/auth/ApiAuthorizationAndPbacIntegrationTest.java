package com.autoparts.erp.auth;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.dto.UserPasswordResetRequest;
import com.autoparts.erp.auth.dto.UserRoleRequest;
import com.autoparts.erp.auth.dto.UserStatusRequest;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.service.PermissionService;
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
@DisplayName("Full API Authorization & PBAC Integration Tests (Roles, Permissions, Hierarchy, Protection)")
class ApiAuthorizationAndPbacIntegrationTest {

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
    private PermissionService permissionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvcAndDb() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        jdbcTemplate.execute("""
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(254) NULL;
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

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
                ('PRODUCTS.VIEW', 'View Products', 'View products', 'PRODUCTS', 'VIEW'),
                ('PRODUCTS.CREATE', 'Create Products', 'Create products', 'PRODUCTS', 'CREATE'),
                ('SALES.VIEW', 'View Sales', 'View sales', 'SALES', 'VIEW'),
                ('SALES.CREATE', 'Create Sales', 'Create sales', 'SALES', 'CREATE'),
                ('SALES.CANCEL', 'Cancel Sales', 'Cancel sales', 'SALES', 'CANCEL'),
                ('SALES_RETURNS.VIEW', 'View Sales Returns', 'View returns', 'SALES_RETURNS', 'VIEW'),
                ('SALES_RETURNS.CREATE', 'Create Sales Returns', 'Create returns', 'SALES_RETURNS', 'CREATE'),
                ('SALES_RETURNS.REFUND', 'Process Sales Refunds', 'Process refunds', 'SALES_RETURNS', 'REFUND'),
                ('PURCHASES.VIEW', 'View Purchases', 'View purchases', 'PURCHASES', 'VIEW'),
                ('PURCHASES.CREATE', 'Create Purchases', 'Create purchases', 'PURCHASES', 'CREATE'),
                ('PURCHASES.RECEIVE', 'Receive Purchases', 'Receive purchases', 'PURCHASES', 'RECEIVE'),
                ('INVENTORY.VIEW', 'View Inventory', 'View inventory', 'INVENTORY', 'VIEW'),
                ('INVENTORY.ADJUST', 'Adjust Inventory', 'Adjust inventory', 'INVENTORY', 'ADJUST'),
                ('REPORTS.VIEW', 'View Operational Reports', 'View reports', 'REPORTS', 'VIEW'),
                ('ROLES.VIEW', 'View Roles', 'View roles', 'ROLES', 'VIEW'),
                ('ROLES.PERMISSIONS_ASSIGN', 'Assign Role Permissions', 'Assign permissions to roles', 'ROLES', 'PERMISSIONS_ASSIGN'),
                ('USERS.VIEW', 'View Users', 'View users', 'USERS', 'VIEW'),
                ('USERS.CREATE', 'Create Users', 'Create users', 'USERS', 'CREATE'),
                ('USERS.UPDATE', 'Update Users', 'Update users', 'USERS', 'UPDATE'),
                ('USERS.STATUS_UPDATE', 'Update User Status', 'Update status', 'USERS', 'STATUS_UPDATE'),
                ('USERS.ROLES_ASSIGN', 'Assign User Roles', 'Assign roles to users', 'USERS', 'ROLES_ASSIGN'),
                ('USERS.RESET_PASSWORD', 'Reset User Password', 'Reset passwords', 'USERS', 'RESET_PASSWORD'),
                ('PERMISSIONS.VIEW', 'View Permissions', 'View permissions', 'PERMISSIONS', 'VIEW'),
                ('AUDIT_LOGS.VIEW', 'View Audit Logs', 'View audit logs', 'AUDIT_LOGS', 'VIEW')
            ON CONFLICT (code) DO NOTHING;

            -- Assign full permissions to ADMIN
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            -- Assign selected permissions to CASHIER
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'CASHIER' AND p.code IN ('PRODUCTS.VIEW', 'SALES.VIEW', 'SALES.CREATE', 'INVENTORY.VIEW')
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            -- Assign selected permissions to MANAGER (Notice: SALES_RETURNS.REFUND is excluded per approved role matrix)
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'MANAGER' AND p.code IN (
                'PRODUCTS.VIEW', 'PRODUCTS.CREATE',
                'SALES.VIEW', 'SALES.CREATE', 'SALES.CANCEL',
                'SALES_RETURNS.VIEW', 'SALES_RETURNS.CREATE',
                'PURCHASES.VIEW', 'PURCHASES.CREATE', 'PURCHASES.RECEIVE',
                'INVENTORY.VIEW', 'INVENTORY.ADJUST',
                'REPORTS.VIEW',
                'USERS.RESET_PASSWORD'
            )
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        permissionService.invalidateAllCaches();
    }

    // =========================================================================
    // 1. GET /api/v1/admin/users/me/permissions
    // =========================================================================

    @Test
    @DisplayName("Self Permissions: Authenticated user can fetch their active effective permissions")
    void testGetMyEffectivePermissions() throws Exception {
        String testUser = "cashier_me_" + UUID.randomUUID().toString().substring(0, 8);
        UUID userUuid = UUID.randomUUID();
        Long userId = userManagementRepository.create(testUser, testUser + "@example.com", passwordEncoder.encode("Test@123"), userUuid);
        userManagementRepository.replaceRoles(userId, Set.of("CASHIER"));

        String token = createBearerToken(testUser, List.of("CASHIER"), userId);

        mockMvc.perform(get("/api/v1/admin/users/me/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(testUser)))
                .andExpect(jsonPath("$.roles", contains("CASHIER")))
                .andExpect(jsonPath("$.permissions", hasItems("SALES.CREATE", "SALES.VIEW", "PRODUCTS.VIEW")))
                .andExpect(jsonPath("$.permissions", not(hasItem("SALES.CANCEL"))));
    }

    @Test
    @DisplayName("Self Permissions: Anonymous request is rejected with 401 Unauthorized")
    void testGetMyEffectivePermissionsAnonymousRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/me/permissions"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 2. Password Reset Hierarchy: Manager -> Cashier vs Manager -> Admin
    // =========================================================================

    @Test
    @DisplayName("Password Reset Hierarchy: Manager CAN reset Cashier password")
    void testManagerCanResetCashierPassword() throws Exception {
        String managerUser = "mgr_" + UUID.randomUUID().toString().substring(0, 8);
        UUID mgrUuid = UUID.randomUUID();
        Long mgrId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("Test@123"), mgrUuid);
        userManagementRepository.replaceRoles(mgrId, Set.of("MANAGER"));

        String cashierUser = "cashier_tgt_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cashierUuid = UUID.randomUUID();
        Long cashierId = userManagementRepository.create(cashierUser, cashierUser + "@example.com", passwordEncoder.encode("Test@123"), cashierUuid);
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));

        String mgrToken = createBearerToken(managerUser, List.of("MANAGER"), mgrId);

        mockMvc.perform(patch("/api/v1/admin/users/" + cashierUuid + "/password")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("NewSecurePassword@123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Password Reset Hierarchy: Manager CANNOT reset Admin password (403 Forbidden)")
    void testManagerCannotResetAdminPassword() throws Exception {
        String managerUser = "mgr_att_" + UUID.randomUUID().toString().substring(0, 8);
        UUID mgrUuid = UUID.randomUUID();
        Long mgrId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("Test@123"), mgrUuid);
        userManagementRepository.replaceRoles(mgrId, Set.of("MANAGER"));

        String adminUser = "adm_tgt_" + UUID.randomUUID().toString().substring(0, 8);
        UUID adminUuid = UUID.randomUUID();
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("Test@123"), adminUuid);
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));

        String mgrToken = createBearerToken(managerUser, List.of("MANAGER"), mgrId);

        mockMvc.perform(patch("/api/v1/admin/users/" + adminUuid + "/password")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("HackedPassword@123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Password Reset Hierarchy: Manager CANNOT reset another Manager's password (403 Forbidden)")
    void testManagerCannotResetOtherManagerPassword() throws Exception {
        String mgr1 = "mgr1_" + UUID.randomUUID().toString().substring(0, 8);
        UUID mgr1Uuid = UUID.randomUUID();
        Long mgr1Id = userManagementRepository.create(mgr1, mgr1 + "@example.com", passwordEncoder.encode("Test@123"), mgr1Uuid);
        userManagementRepository.replaceRoles(mgr1Id, Set.of("MANAGER"));

        String mgr2 = "mgr2_" + UUID.randomUUID().toString().substring(0, 8);
        UUID mgr2Uuid = UUID.randomUUID();
        Long mgr2Id = userManagementRepository.create(mgr2, mgr2 + "@example.com", passwordEncoder.encode("Test@123"), mgr2Uuid);
        userManagementRepository.replaceRoles(mgr2Id, Set.of("MANAGER"));

        String mgrToken = createBearerToken(mgr1, List.of("MANAGER"), mgr1Id);

        mockMvc.perform(patch("/api/v1/admin/users/" + mgr2Uuid + "/password")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("NewPassword@123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Password Reset Hierarchy: Cashier CANNOT reset any password (403 Forbidden)")
    void testCashierCannotResetAnyPassword() throws Exception {
        String cashier1 = "csh1_" + UUID.randomUUID().toString().substring(0, 8);
        UUID csh1Uuid = UUID.randomUUID();
        Long csh1Id = userManagementRepository.create(cashier1, cashier1 + "@example.com", passwordEncoder.encode("Test@123"), csh1Uuid);
        userManagementRepository.replaceRoles(csh1Id, Set.of("CASHIER"));

        String cashier2 = "csh2_" + UUID.randomUUID().toString().substring(0, 8);
        UUID csh2Uuid = UUID.randomUUID();
        Long cashier2Id = userManagementRepository.create(cashier2, cashier2 + "@example.com", passwordEncoder.encode("Test@123"), csh2Uuid);
        userManagementRepository.replaceRoles(cashier2Id, Set.of("CASHIER"));

        String cashierToken = createBearerToken(cashier1, List.of("CASHIER"), csh1Id);

        mockMvc.perform(patch("/api/v1/admin/users/" + csh2Uuid + "/password")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("NewPassword@123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Password Reset Defense: Self-reset via administrative endpoint is rejected with 400 Bad Request")
    void testSelfPasswordResetViaAdminEndpointRejected() throws Exception {
        String adminUser = "self_adm_" + UUID.randomUUID().toString().substring(0, 8);
        UUID adminUuid = UUID.randomUUID();
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("Test@123"), adminUuid);
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));

        String adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        // Attempting to administratively reset one's own password should be rejected with 400 Bad Request
        mockMvc.perform(patch("/api/v1/admin/users/" + adminUuid + "/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("SelfResetPass@123"))))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. PBAC Method Authorization & Least Privilege Enforcement
    // =========================================================================

    @Test
    @DisplayName("PBAC: Cashier cannot cancel sales (403 Forbidden)")
    void testCashierCannotCancelSale() throws Exception {
        String cashier = "cashier_cancel_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cashierUuid = UUID.randomUUID();
        Long cashierId = userManagementRepository.create(cashier, cashier + "@example.com", passwordEncoder.encode("Test@123"), cashierUuid);
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));

        String token = createBearerToken(cashier, List.of("CASHIER"), cashierId);

        mockMvc.perform(post("/api/v1/sales/" + UUID.randomUUID() + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PBAC: Manager cannot process sales refunds (403 Forbidden - Admin only)")
    void testManagerCannotProcessRefunds() throws Exception {
        String manager = "mgr_refund_" + UUID.randomUUID().toString().substring(0, 8);
        UUID mgrUuid = UUID.randomUUID();
        Long mgrId = userManagementRepository.create(manager, manager + "@example.com", passwordEncoder.encode("Test@123"), mgrUuid);
        userManagementRepository.replaceRoles(mgrId, Set.of("MANAGER"));

        String token = createBearerToken(manager, List.of("MANAGER"), mgrId);

        mockMvc.perform(post("/api/v1/sales-returns/" + UUID.randomUUID() + "/refund")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundMethod\": \"CASH\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PBAC: Cashier cannot view reports (403 Forbidden)")
    void testCashierCannotViewReports() throws Exception {
        String cashier = "cashier_report_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cashierUuid = UUID.randomUUID();
        Long cashierId = userManagementRepository.create(cashier, cashier + "@example.com", passwordEncoder.encode("Test@123"), cashierUuid);
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));

        String token = createBearerToken(cashier, List.of("CASHIER"), cashierId);

        mockMvc.perform(get("/api/v1/reports/sales-summary")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-24"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PBAC: Cashier cannot adjust inventory (403 Forbidden)")
    void testCashierCannotAdjustInventory() throws Exception {
        String cashier = "cashier_adj_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cashierUuid = UUID.randomUUID();
        Long cashierId = userManagementRepository.create(cashier, cashier + "@example.com", passwordEncoder.encode("Test@123"), cashierUuid);
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));

        String token = createBearerToken(cashier, List.of("CASHIER"), cashierId);

        mockMvc.perform(post("/api/v1/inventory/products/" + UUID.randomUUID() + "/adjustments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityChange\": 5, \"reason\": \"Found stock\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PBAC: Cashier cannot access permission management APIs (403 Forbidden)")
    void testCashierCannotAccessPermissionManagement() throws Exception {
        String cashier = "cashier_mgmt_" + UUID.randomUUID().toString().substring(0, 8);
        UUID cashierUuid = UUID.randomUUID();
        Long cashierId = userManagementRepository.create(cashier, cashier + "@example.com", passwordEncoder.encode("Test@123"), cashierUuid);
        userManagementRepository.replaceRoles(cashierId, Set.of("CASHIER"));

        String token = createBearerToken(cashier, List.of("CASHIER"), cashierId);

        mockMvc.perform(get("/api/v1/admin/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PBAC: Manager cannot modify role permissions (403 Forbidden)")
    void testManagerCannotModifyRolePermissions() throws Exception {
        String manager = "mgr_role_perm_" + UUID.randomUUID().toString().substring(0, 8);
        UUID mgrUuid = UUID.randomUUID();
        Long mgrId = userManagementRepository.create(manager, manager + "@example.com", passwordEncoder.encode("Test@123"), mgrUuid);
        userManagementRepository.replaceRoles(mgrId, Set.of("MANAGER"));

        String token = createBearerToken(manager, List.of("MANAGER"), mgrId);

        mockMvc.perform(put("/api/v1/admin/roles/" + UUID.randomUUID() + "/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\": [\"PRODUCTS.VIEW\"]}"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. Last Active Administrator Protection
    // =========================================================================

    @Test
    @DisplayName("Last Admin Protection: Deactivating the sole active administrator is rejected with 409 Conflict")
    void testDeactivateLastAdminFailsWithConflict() throws Exception {
        // Ensure there is only 1 active admin
        List<Long> adminIds = jdbcTemplate.queryForList(
                "SELECT u.id FROM users u JOIN user_roles ur ON u.id = ur.user_id JOIN roles r ON ur.role_id = r.id WHERE r.name = 'ADMIN' AND u.enabled = TRUE",
                Long.class
        );

        Long soleAdminId = adminIds.get(0);
        UUID soleAdminUuid = jdbcTemplate.queryForObject("SELECT uuid FROM users WHERE id = ?", UUID.class, soleAdminId);
        String soleAdminUsername = jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class, soleAdminId);

        // Create a temporary secondary admin caller so that the "cannot deactivate self" check passes
        String callerAdminUser = "caller_adm_" + UUID.randomUUID().toString().substring(0, 8);
        UUID callerAdminUuid = UUID.randomUUID();
        Long callerAdminId = userManagementRepository.create(callerAdminUser, callerAdminUser + "@example.com", passwordEncoder.encode("Test@123"), callerAdminUuid);
        userManagementRepository.replaceRoles(callerAdminId, Set.of("ADMIN"));

        // Now deactivating callerAdmin leaves soleAdmin as the only admin remaining.
        // Let's deactivate callerAdmin first.
        String callerToken = createBearerToken(callerAdminUser, List.of("ADMIN"), callerAdminId);

        // Deactivating callerAdmin is allowed because soleAdmin is still active
        mockMvc.perform(patch("/api/v1/admin/users/" + callerAdminUuid + "/status")
                        .header("Authorization", "Bearer " + createBearerToken(soleAdminUsername, List.of("ADMIN"), soleAdminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusRequest(false))))
                .andExpect(status().isOk());

        // Re-enable callerAdmin to be the caller:
        userManagementRepository.updateEnabled(callerAdminUuid, true);

        // Deactivate soleAdmin -> succeeds (leaves callerAdmin active)
        mockMvc.perform(patch("/api/v1/admin/users/" + soleAdminUuid + "/status")
                        .header("Authorization", "Bearer " + createBearerToken(callerAdminUser, List.of("ADMIN"), callerAdminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusRequest(false))))
                .andExpect(status().isOk());

        // Now callerAdmin is the SOLE active admin!
        // Re-enable soleAdmin using direct repo so we can test conflict:
        userManagementRepository.updateEnabled(soleAdminUuid, true);
        userManagementRepository.updateEnabled(callerAdminUuid, false);

        // Because callerAdmin is disabled, countActiveAdmins is exactly 1 (soleAdmin)!
        // Attempting to deactivate soleAdmin now must fail with 409 Conflict:
        mockMvc.perform(patch("/api/v1/admin/users/" + soleAdminUuid + "/status")
                        .header("Authorization", "Bearer " + createBearerToken(callerAdminUser, List.of("ADMIN"), callerAdminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusRequest(false))))
                .andExpect(status().isConflict());

        // Re-enable soleAdmin just in case
        userManagementRepository.updateEnabled(soleAdminUuid, true);
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
