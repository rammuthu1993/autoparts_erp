package com.autoparts.erp.auth;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.dto.AssignRolePermissionsRequest;
import com.autoparts.erp.auth.dto.RolePermissionsResponse;
import com.autoparts.erp.auth.dto.UserEffectivePermissionsResponse;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.PermissionRepository;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.common.exception.ClientValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@DisplayName("PermissionService PBAC, Role Freshness, and Cache Invalidation Integration Tests")
class PermissionServiceIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/autoparts_erp_test";

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
    }

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserManagementRepository userManagementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        // Ensure V39 schema and tables exist in the test DB
        jdbcTemplate.execute("""
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

            -- Ensure core permissions are seeded
            INSERT INTO permissions (code, name, description, module, action) VALUES
                ('PRODUCTS.VIEW', 'View Products', 'View products', 'PRODUCTS', 'VIEW'),
                ('PRODUCTS.CREATE', 'Create Products', 'Create products', 'PRODUCTS', 'CREATE'),
                ('SALES.VIEW', 'View Sales', 'View sales', 'SALES', 'VIEW'),
                ('SALES.CREATE', 'Create Sales', 'Create sales', 'SALES', 'CREATE'),
                ('SALES.CANCEL', 'Cancel Sales', 'Cancel sales', 'SALES', 'CANCEL'),
                ('PURCHASES.CREATE', 'Create Purchases', 'Create purchases', 'PURCHASES', 'CREATE'),
                ('ROLES.PERMISSIONS_ASSIGN', 'Assign Role Permissions', 'Assign permissions to roles', 'ROLES', 'PERMISSIONS_ASSIGN'),
                ('USERS.ROLES_ASSIGN', 'Assign User Roles', 'Assign roles to users', 'USERS', 'ROLES_ASSIGN'),
                ('USERS.STATUS_UPDATE', 'Update User Status', 'Update user status', 'USERS', 'STATUS_UPDATE')
            ON CONFLICT (code) DO NOTHING;

            -- Assign full permissions to ADMIN
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            -- Assign selected permissions to CASHIER
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'CASHIER' AND p.code IN ('PRODUCTS.VIEW', 'SALES.VIEW', 'SALES.CREATE')
            ON CONFLICT (role_id, permission_id) DO NOTHING;

            -- Assign selected permissions to MANAGER
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
            WHERE r.name = 'MANAGER' AND p.code IN ('PRODUCTS.VIEW', 'PRODUCTS.CREATE', 'SALES.VIEW', 'SALES.CREATE', 'SALES.CANCEL', 'PURCHASES.CREATE')
            ON CONFLICT (role_id, permission_id) DO NOTHING;
        """);

        permissionService.invalidateAllCaches();
    }

    @Test
    @DisplayName("CASHIER role receives restricted permissions and lacks elevated privileges")
    void testCashierPermissions() {
        String testUsername = "cashier_perm_test_" + UUID.randomUUID().toString().substring(0, 8);
        UUID userUuid = UUID.randomUUID();
        Long userId = userManagementRepository.create(testUsername, "cashier_test@example.com", passwordEncoder.encode("Test@123"), userUuid);
        userManagementRepository.replaceRoles(userId, Set.of("CASHIER"));

        assertTrue(permissionService.hasPermission(userId, "SALES.CREATE"));
        assertTrue(permissionService.hasPermission(userId, "SALES.VIEW"));
        assertTrue(permissionService.hasPermission(userId, "PRODUCTS.VIEW"));

        assertFalse(permissionService.hasPermission(userId, "SALES.CANCEL"));
        assertFalse(permissionService.hasPermission(userId, "PURCHASES.CREATE"));
        assertFalse(permissionService.hasPermission(userId, "ROLES.PERMISSIONS_ASSIGN"));
    }

    @Test
    @DisplayName("ADMIN role possesses all permissions including governance permissions")
    void testAdminPermissions() {
        String testUsername = "admin_perm_test_" + UUID.randomUUID().toString().substring(0, 8);
        UUID userUuid = UUID.randomUUID();
        Long userId = userManagementRepository.create(testUsername, "admin_test@example.com", passwordEncoder.encode("Test@123"), userUuid);
        userManagementRepository.replaceRoles(userId, Set.of("ADMIN"));

        assertTrue(permissionService.hasPermission(userId, "SALES.CREATE"));
        assertTrue(permissionService.hasPermission(userId, "SALES.CANCEL"));
        assertTrue(permissionService.hasPermission(userId, "PURCHASES.CREATE"));
        assertTrue(permissionService.hasPermission(userId, "ROLES.PERMISSIONS_ASSIGN"));
        assertTrue(permissionService.hasPermission(userId, "USERS.ROLES_ASSIGN"));
        assertTrue(permissionService.hasPermission(userId, "USERS.STATUS_UPDATE"));
    }

    @Test
    @DisplayName("Dynamic Role Freshness: Upgrading Cashier to Manager in DB takes effect immediately without re-login")
    void testDynamicRoleFreshness() {
        String testUsername = "dynamic_user_" + UUID.randomUUID().toString().substring(0, 8);
        UUID userUuid = UUID.randomUUID();
        Long userId = userManagementRepository.create(testUsername, "dynamic_test@example.com", passwordEncoder.encode("Test@123"), userUuid);
        userManagementRepository.replaceRoles(userId, Set.of("CASHIER"));

        // Cashier cannot cancel sales initially
        assertFalse(permissionService.hasPermission(userId, "SALES.CANCEL"));

        // Promote user to MANAGER in DB and evict role cache
        userManagementRepository.replaceRoles(userId, Set.of("MANAGER"));
        permissionService.evictUserRoleCache(userId);

        // Immediate promotion takes effect: user can now cancel sales without acquiring a new JWT token!
        assertTrue(permissionService.hasPermission(userId, "SALES.CANCEL"));
        assertTrue(permissionService.hasPermission(userId, "PURCHASES.CREATE"));
    }

    @Test
    @DisplayName("Dynamic Permission Freshness: Updating role permissions takes effect immediately for assigned users")
    void testDynamicPermissionFreshness() {
        String testUsername = "cashier_perm_freshness_" + UUID.randomUUID().toString().substring(0, 8);
        UUID userUuid = UUID.randomUUID();
        Long userId = userManagementRepository.create(testUsername, "freshness@example.com", passwordEncoder.encode("Test@123"), userUuid);
        userManagementRepository.replaceRoles(userId, Set.of("CASHIER"));

        assertFalse(permissionService.hasPermission(userId, "PURCHASES.CREATE"));

        // Get CASHIER role UUID
        UUID cashierRoleUuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM roles WHERE name = 'CASHIER'",
                UUID.class
        );

        // Grant PURCHASES.CREATE to CASHIER role dynamically
        permissionService.assignRolePermissions(cashierRoleUuid, new AssignRolePermissionsRequest(
                Set.of("PRODUCTS.VIEW", "SALES.VIEW", "SALES.CREATE", "PURCHASES.CREATE")
        ));

        // CASHIER user now immediately has PURCHASES.CREATE without needing cache expiration or re-login
        assertTrue(permissionService.hasPermission(userId, "PURCHASES.CREATE"));

        // Roll back CASHIER permissions
        permissionService.assignRolePermissions(cashierRoleUuid, new AssignRolePermissionsRequest(
                Set.of("PRODUCTS.VIEW", "SALES.VIEW", "SALES.CREATE")
        ));
        assertFalse(permissionService.hasPermission(userId, "PURCHASES.CREATE"));
    }

    @Test
    @DisplayName("Admin Role Protection: Attempting to remove critical permissions from ADMIN is rejected")
    void testAdminProtectionRejectsRemovalOfCriticalPermissions() {
        UUID adminRoleUuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM roles WHERE name = 'ADMIN'",
                UUID.class
        );

        // Attempt to remove ROLES.PERMISSIONS_ASSIGN from ADMIN
        assertThrows(ClientValidationException.class, () ->
                permissionService.assignRolePermissions(adminRoleUuid, new AssignRolePermissionsRequest(
                        Set.of("PRODUCTS.VIEW", "SALES.VIEW")
                ))
        );
    }

    @Test
    @DisplayName("Assigning invalid or unrecognized permission code is rejected")
    void testAssigningInvalidPermissionCodeRejected() {
        UUID cashierRoleUuid = jdbcTemplate.queryForObject(
                "SELECT uuid FROM roles WHERE name = 'CASHIER'",
                UUID.class
        );

        assertThrows(ClientValidationException.class, () ->
                permissionService.assignRolePermissions(cashierRoleUuid, new AssignRolePermissionsRequest(
                        Set.of("INVALID.NON_EXISTENT.PERMISSION")
                ))
        );
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
