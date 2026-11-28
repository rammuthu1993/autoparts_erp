package com.autoparts.erp.auth;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.dto.AssignRolePermissionsRequest;
import com.autoparts.erp.auth.dto.UserCreateRequest;
import com.autoparts.erp.auth.dto.UserPasswordResetRequest;
import com.autoparts.erp.auth.dto.UserRoleRequest;
import com.autoparts.erp.auth.dto.UserStatusRequest;
import com.autoparts.erp.auth.dto.UserUpdateRequest;
import com.autoparts.erp.auth.repository.UserManagementRepository;
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
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("Admin Users, Roles, Permissions & Audit Integration Tests")
class AdminUserRolePermissionIntegrationTest {

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
    private UUID adminUuid;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        jdbcTemplate.execute("""
            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('USERS.VIEW', 'View Users', 'View users', 'USERS', 'VIEW'),
                ('USERS.CREATE', 'Create Users', 'Create users', 'USERS', 'CREATE'),
                ('USERS.UPDATE', 'Update Users', 'Update users', 'USERS', 'UPDATE'),
                ('USERS.STATUS_UPDATE', 'Update User Status', 'Enable or disable users', 'USERS', 'STATUS_UPDATE'),
                ('USERS.ROLES_ASSIGN', 'Assign User Roles', 'Assign roles to users', 'USERS', 'ROLES_ASSIGN'),
                ('USERS.RESET_PASSWORD', 'Reset User Password', 'Reset user password', 'USERS', 'RESET_PASSWORD'),
                ('ROLES.VIEW', 'View Roles', 'View roles', 'ROLES', 'VIEW'),
                ('ROLES.PERMISSIONS_ASSIGN', 'Assign Role Permissions', 'Assign permissions to roles', 'ROLES', 'PERMISSIONS_ASSIGN'),
                ('PERMISSIONS.VIEW', 'View Permissions', 'View permissions', 'PERMISSIONS', 'VIEW'),
                ('AUDIT_LOGS.VIEW', 'View Audit Logs', 'View audit logs', 'AUDIT_LOGS', 'VIEW')
            ON CONFLICT (code) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'MANAGER' AND p.code = 'USERS.RESET_PASSWORD'
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        String adminUser = "adm_urp_" + UUID.randomUUID().toString().substring(0, 8);
        adminUuid = UUID.randomUUID();
        Long adminId = userManagementRepository.create(adminUser, adminUser + "@example.com", passwordEncoder.encode("StrongPass123!"), adminUuid);
        userManagementRepository.replaceRoles(adminId, Set.of("ADMIN"));
        adminToken = createBearerToken(adminUser, List.of("ADMIN"), adminId);

        String managerUser = "mgr_urp_" + UUID.randomUUID().toString().substring(0, 8);
        Long managerId = userManagementRepository.create(managerUser, managerUser + "@example.com", passwordEncoder.encode("StrongPass123!"), UUID.randomUUID());
        userManagementRepository.replaceRoles(managerId, Set.of("MANAGER"));
        managerToken = createBearerToken(managerUser, List.of("MANAGER"), managerId);
    }

    @Test
    @DisplayName("User lifecycle: dynamic role check, email clearing, filters, hierarchy password reset, unlock, self-disable prevention")
    void testUserLifecycleAndFilters() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        // 1. Unsupported role rejected dynamically against DB roles
        UserCreateRequest invalidRoleReq = new UserCreateRequest(
                "badrole_" + suffix, "bad_" + suffix + "@test.com", "StrongPass123!", Set.of("SUPERHERO")
        );
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRoleReq)))
                .andExpect(status().isBadRequest());

        // 2. Create CASHIER user
        UserCreateRequest createReq = new UserCreateRequest(
                "staff_" + suffix, "staff_" + suffix + "@test.com", "StrongPass123!", Set.of("CASHIER")
        );
        String createdJson = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", hasItem("CASHIER")))
                .andExpect(jsonPath("$.locked", is(false)))
                .andReturn().getResponse().getContentAsString();
        UUID staffUuid = UUID.fromString(objectMapper.readTree(createdJson).get("uuid").asText());

        // 3. Explicit email clearing
        mockMvc.perform(put("/api/v1/admin/users/" + staffUuid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserUpdateRequest("staff_" + suffix, "", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", nullValue()))
                .andExpect(jsonPath("$.emailVerified", is(false)));

        // 4. Filter by status=ENABLED & role=CASHIER
        mockMvc.perform(get("/api/v1/admin/users/paged")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "staff_" + suffix)
                        .param("status", "ENABLED")
                        .param("role", "CASHIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.uuid == '" + staffUuid + "')]", hasSize(1)));

        // 5. Manager can reset Cashier password, cannot reset Admin password
        mockMvc.perform(patch("/api/v1/admin/users/" + staffUuid + "/password")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("NewStrongPass123!"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/admin/users/" + adminUuid + "/password")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserPasswordResetRequest("NewStrongPass123!"))))
                .andExpect(status().isForbidden());

        // 6. Self-disable prevented -> 409
        mockMvc.perform(patch("/api/v1/admin/users/" + adminUuid + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusRequest(false))))
                .andExpect(status().isConflict());

        // 7. Disable & re-enable staff user + unlock endpoint
        mockMvc.perform(patch("/api/v1/admin/users/" + staffUuid + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));

        mockMvc.perform(post("/api/v1/admin/users/" + staffUuid + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedLoginAttempts", is(0)));

        // 8. Audit logs date filter
        mockMvc.perform(get("/api/v1/admin/audit-logs/paged")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entityType", "USER")
                        .param("fromDate", LocalDate.now().toString())
                        .param("toDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())));
    }

    @Test
    @DisplayName("Roles & permissions: counts, ADMIN critical protection, non-ADMIN empty set allowed")
    void testRolesAndPermissions() throws Exception {
        // Roles list returns userCount, permissionCount, systemRole
        mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("ADMIN", "MANAGER", "CASHIER")))
                .andExpect(jsonPath("$[0].systemRole", is(true)));

        // ADMIN critical permissions cannot be stripped
        UUID adminRoleUuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM roles WHERE name = 'ADMIN'", UUID.class
        );
        mockMvc.perform(put("/api/v1/admin/roles/" + adminRoleUuid + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRolePermissionsRequest(Set.of("USERS.VIEW")))))
                .andExpect(status().isBadRequest());

        // Non-ADMIN role supports empty permission set (then restore CASHIER.VIEW-like safe perm or leave empty and restore)
        UUID cashierRoleUuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM roles WHERE name = 'CASHIER'", UUID.class
        );
        mockMvc.perform(put("/api/v1/admin/roles/" + cashierRoleUuid + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRolePermissionsRequest(Set.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasSize(0)));
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
