package com.autoparts.erp.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErpAiExecutionContext Defensive Immutability and Authorization Tests")
class ErpAiExecutionContextTest {

    @Test
    @DisplayName("Guarantees defensive immutability using Set.copyOf: mutations to input collection do not affect context")
    void shouldGuaranteeDefensiveImmutabilityFromInputCollection() {
        List<String> mutableRoles = new ArrayList<>();
        mutableRoles.add("ROLE_CASHIER");

        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext context = ErpAiExecutionContext.create(conversationId, "john_cashier", mutableRoles);

        assertTrue(context.hasRole("ROLE_CASHIER"));
        assertFalse(context.hasRole("ROLE_ADMIN"));

        // Mutate original list
        mutableRoles.add("ROLE_ADMIN");

        // Context must NOT reflect the mutation
        assertFalse(context.hasRole("ROLE_ADMIN"), "Mutating the original list must not affect the immutable context");
        assertEquals(Set.of("ROLE_CASHIER"), context.authorities());
    }

    @Test
    @DisplayName("Guarantees immutability on returned authorities set")
    void shouldPreventMutationOfAuthoritiesSet() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext context = ErpAiExecutionContext.create(conversationId, "admin_user", List.of("ROLE_ADMIN"));

        Set<String> authorities = context.authorities();
        assertThrows(UnsupportedOperationException.class, () -> authorities.add("ROLE_CASHIER"));
    }

    @Test
    @DisplayName("Factory method validates non-null conversationId, userSubject and grantedRoles")
    void shouldValidateInputs() {
        UUID convId = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> ErpAiExecutionContext.create(null, "user", List.of("ROLE_ADMIN")));

        assertThrows(IllegalArgumentException.class,
                () -> ErpAiExecutionContext.create(convId, null, List.of("ROLE_ADMIN")));

        assertThrows(IllegalArgumentException.class,
                () -> ErpAiExecutionContext.create(convId, "   ", List.of("ROLE_ADMIN")));

        assertThrows(NullPointerException.class,
                () -> ErpAiExecutionContext.create(convId, "user", null));
    }

    @Test
    @DisplayName("Filters out null and blank role strings and normalizes whitespace")
    void shouldFilterNullAndBlankAuthorities() {
        List<String> dirtyRoles = new ArrayList<>();
        dirtyRoles.add("  ROLE_ADMIN  ");
        dirtyRoles.add(null);
        dirtyRoles.add("");
        dirtyRoles.add("   ");
        dirtyRoles.add("ROLE_MANAGER");

        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext context = ErpAiExecutionContext.create(conversationId, "  admin_user  ", dirtyRoles);

        assertEquals("admin_user", context.userSubject());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_MANAGER"), context.authorities());
    }

    @Test
    @DisplayName("Supports hasRole check with or without ROLE_ prefix")
    void shouldSupportPrefixAgnosticRoleCheck() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext context = ErpAiExecutionContext.create(conversationId, "user", List.of("ROLE_MANAGER"));

        assertTrue(context.hasRole("ROLE_MANAGER"));
        assertTrue(context.hasRole("MANAGER"));
        assertFalse(context.hasRole("ADMIN"));
        assertFalse(context.hasRole(""));
        assertFalse(context.hasRole(null));
    }

    @Test
    @DisplayName("Checks any role matching correctly")
    void shouldCheckAnyRoleCorrectly() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext cashierContext = ErpAiExecutionContext.create(conversationId, "cashier", List.of("ROLE_CASHIER"));

        assertTrue(cashierContext.hasAnyRole("ROLE_ADMIN", "ROLE_CASHIER"));
        assertFalse(cashierContext.hasAnyRole("ROLE_ADMIN", "ROLE_MANAGER"));
        assertFalse(cashierContext.hasAnyRole((String[]) null));
        assertFalse(cashierContext.hasAnyRole());
    }

    @Test
    @DisplayName("requireAnyRole throws ToolAuthorizationException when roles are not satisfied")
    void shouldThrowToolAuthorizationExceptionOnUnauthorized() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext userContext = ErpAiExecutionContext.create(conversationId, "standard_user", List.of("ROLE_USER"));

        ToolAuthorizationException ex = assertThrows(ToolAuthorizationException.class,
                () -> userContext.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER"));

        assertTrue(ex.getMessage().contains("Access denied"));
        assertTrue(ex.getMessage().contains("ROLE_ADMIN"));
        assertTrue(ex.getMessage().contains("ROLE_USER"));
    }

    @Test
    @DisplayName("requireAnyRole passes silently when required role is held")
    void shouldPassWhenRequiredRoleIsHeld() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext managerContext = ErpAiExecutionContext.create(conversationId, "manager_user", List.of("ROLE_MANAGER"));

        assertDoesNotThrow(() -> managerContext.requireAnyRole("ROLE_ADMIN", "ROLE_MANAGER"));
    }

    @Test
    @DisplayName("Convenience helpers: isSalesAccess, isAdminOrManager, isAdminOnly")
    void shouldVerifyConvenienceRoleHelpers() {
        UUID convId = UUID.randomUUID();

        ErpAiExecutionContext cashier = ErpAiExecutionContext.create(convId, "c", List.of("ROLE_CASHIER"));
        assertTrue(cashier.isSalesAccess());
        assertFalse(cashier.isAdminOrManager());
        assertFalse(cashier.isAdminOnly());

        ErpAiExecutionContext manager = ErpAiExecutionContext.create(convId, "m", List.of("ROLE_MANAGER"));
        assertTrue(manager.isSalesAccess());
        assertTrue(manager.isAdminOrManager());
        assertFalse(manager.isAdminOnly());

        ErpAiExecutionContext admin = ErpAiExecutionContext.create(convId, "a", List.of("ROLE_ADMIN"));
        assertTrue(admin.isSalesAccess());
        assertTrue(admin.isAdminOrManager());
        assertTrue(admin.isAdminOnly());

        ErpAiExecutionContext customer = ErpAiExecutionContext.create(convId, "u", List.of("ROLE_CUSTOMER"));
        assertFalse(customer.isSalesAccess());
        assertFalse(customer.isAdminOrManager());
        assertFalse(customer.isAdminOnly());
    }
}
