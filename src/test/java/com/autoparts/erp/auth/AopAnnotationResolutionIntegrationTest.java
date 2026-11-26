package com.autoparts.erp.auth;

import com.autoparts.erp.AutopartsErpApplication;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.security.AuthenticatedUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AutopartsErpApplication.class)
@Import(AopAnnotationResolutionIntegrationTest.TestAopBeansConfig.class)
@DisplayName("AOP Method and Class Annotation Resolution Integration Tests")
class AopAnnotationResolutionIntegrationTest {

    private static final String TEST_DB_URL = "jdbc:postgresql://localhost:5432/autoparts_erp_test";

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

    public interface SampleProxyServiceInterface {
        String interfaceAnnotatedMethod();
        String implementationAnnotatedMethod();
    }

    @Service
    @RequiresPermission("PRODUCTS.VIEW")
    public static class SampleClassLevelService {
        public String defaultClassAnnotated() {
            return "class-annotated-ok";
        }

        @RequiresPermission("PRODUCTS.CREATE")
        public String methodOverride() {
            return "method-override-ok";
        }
    }

    @Service
    public static class SampleProxyServiceImpl implements SampleProxyServiceInterface {
        @Override
        @RequiresPermission("SALES.VIEW")
        public String interfaceAnnotatedMethod() {
            return "interface-method-ok";
        }

        @Override
        @RequiresPermission("SALES.CREATE")
        public String implementationAnnotatedMethod() {
            return "impl-method-ok";
        }
    }

    @TestConfiguration
    public static class TestAopBeansConfig {
        @Bean
        public SampleClassLevelService sampleClassLevelService() {
            return new SampleClassLevelService();
        }

        @Bean
        public SampleProxyServiceInterface sampleProxyService() {
            return new SampleProxyServiceImpl();
        }
    }

    @Autowired
    private SampleClassLevelService classLevelService;

    @Autowired
    private SampleProxyServiceInterface proxyService;

    @Autowired
    private PermissionService permissionService;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Unauthenticated caller invoking @RequiresPermission throws AccessDeniedException")
    void testUnauthenticatedCallThrowsAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> classLevelService.defaultClassAnnotated());
        assertThrows(AccessDeniedException.class, () -> proxyService.interfaceAnnotatedMethod());
    }

    @Test
    @DisplayName("Class-level annotation is resolved when method has no annotation")
    void testClassLevelAnnotationResolved() {
        // Authenticate with PRODUCTS.VIEW (matches class-level @RequiresPermission("PRODUCTS.VIEW"))
        authenticateAs("admin_test_user", 1L, List.of("ADMIN"));

        assertEquals("class-annotated-ok", classLevelService.defaultClassAnnotated());
    }

    @Test
    @DisplayName("Method-level annotation overrides class-level annotation")
    void testMethodLevelAnnotationOverridesClassLevel() {
        // Authenticate as a user who only has PRODUCTS.VIEW but NOT PRODUCTS.CREATE
        authenticateAs("viewer_user", 99999L, List.of("VIEWER_ONLY"));

        // Class level succeeds if user has it, but method override requires PRODUCTS.CREATE
        assertThrows(AccessDeniedException.class, () -> classLevelService.methodOverride());
    }

    @Test
    @DisplayName("Proxied interface bean resolves method-level annotation on target implementation")
    void testProxiedBeanResolvesImplementationMethod() {
        authenticateAs("admin_test_user", 1L, List.of("ADMIN"));

        assertEquals("interface-method-ok", proxyService.interfaceAnnotatedMethod());
        assertEquals("impl-method-ok", proxyService.implementationAnnotatedMethod());
    }

    private void authenticateAs(String username, Long userId, List<String> roles) {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        com.autoparts.erp.security.AuthenticatedUserDetails principal =
                new AuthenticatedUserDetails(userId, username, "N/A", authorities, true, true);

        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
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
