package com.autoparts.erp.audit.serviceimpl;

import com.autoparts.erp.audit.repository.AuditLogRepository;
import com.autoparts.erp.auth.security.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private CurrentUserService currentUserService;

    private ObjectMapper objectMapper;
    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditLogService = new AuditLogServiceImpl(
                auditLogRepository,
                currentUserService,
                objectMapper
        );
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logWithAuthenticatedUserCapturesUserContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                "cashier1",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(currentUserService.getCurrentUserId()).thenReturn(42L);

        auditLogService.log(
                "CREATE",
                "PRODUCT",
                "PRD-001",
                "Product created",
                null,
                Map.of("name", "Brake Pad", "price", 1500)
        );

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> newJsonCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditLogRepository).insert(
                userIdCaptor.capture(),
                usernameCaptor.capture(),
                eq("CREATE"),
                eq("PRODUCT"),
                eq("PRD-001"),
                eq("Product created"),
                isNull(),
                newJsonCaptor.capture()
        );

        assertEquals(42L, userIdCaptor.getValue());
        assertEquals("cashier1", usernameCaptor.getValue());
        assertNotNull(newJsonCaptor.getValue());
        assertTrue(newJsonCaptor.getValue().contains("Brake Pad"));
    }

    @Test
    void logWhenCurrentUserServiceFailsPreservesUserActorTypeAndNullUserId() {
        var auth = new UsernamePasswordAuthenticationToken(
                "cashier2",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(currentUserService.getCurrentUserId()).thenThrow(new RuntimeException("Database timeout resolving user"));

        auditLogService.log(
                "UPDATE",
                "PRODUCT",
                "PRD-002",
                "Price updated",
                Map.of("price", 100),
                Map.of("price", 120)
        );

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditLogRepository).insert(
                userIdCaptor.capture(),
                usernameCaptor.capture(),
                eq("UPDATE"),
                eq("PRODUCT"),
                eq("PRD-002"),
                eq("Price updated"),
                anyString(),
                anyString()
        );

        assertNull(userIdCaptor.getValue(), "User ID must be null if resolution fails, respecting FK constraint");
        assertEquals("cashier2", usernameCaptor.getValue(), "Username must be preserved from authenticated principal");
    }

    @Test
    void logWithoutAuthenticatedUserFallsBackToSystemActor() {
        // No authentication in SecurityContext
        auditLogService.log(
                "ADJUST",
                "INVENTORY",
                "INV-100",
                "Automated stock reconciliation",
                Map.of("qty", 10),
                Map.of("qty", 12)
        );

        verify(auditLogRepository).insert(
                isNull(),
                eq("SYSTEM"),
                eq("ADJUST"),
                eq("INVENTORY"),
                eq("INV-100"),
                eq("Automated stock reconciliation"),
                anyString(),
                anyString()
        );
    }

    @Test
    void logSystemCapturesCustomSystemIdentifier() {
        auditLogService.logSystem(
                "NIGHTLY_BATCH_JOB",
                "UPDATE",
                "DAILY_CLOSING",
                "2026-11-20",
                "Scheduled closing completed",
                null,
                Map.of("status", "CLOSED")
        );

        verify(auditLogRepository).insert(
                isNull(),
                eq("NIGHTLY_BATCH_JOB"),
                eq("UPDATE"),
                eq("DAILY_CLOSING"),
                eq("2026-11-20"),
                eq("Scheduled closing completed"),
                isNull(),
                anyString()
        );
    }

    @Test
    void logWithActorPreservesExplicitUserContextAcrossAsyncHandoff() {
        // Testing preservation of actor context during asynchronous event handling
        auditLogService.logWithActor(
                "USER",
                99L,
                "admin-user",
                "APPROVE",
                "PURCHASE_ORDER",
                "PO-9001",
                "Async approved by background event listener",
                null,
                Map.of("approved", true)
        );

        verify(auditLogRepository).insert(
                eq(99L),
                eq("admin-user"),
                eq("APPROVE"),
                eq("PURCHASE_ORDER"),
                eq("PO-9001"),
                eq("Async approved by background event listener"),
                isNull(),
                anyString()
        );
    }

    @Test
    void logValidatesRequiredActionAndEntityType() {
        assertThrows(IllegalArgumentException.class, () ->
                auditLogService.logWithActor("USER", 1L, "u", "", "PRODUCT", "1", "desc", null, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                auditLogService.logWithActor("USER", 1L, "u", "CREATE", null, "1", "desc", null, null)
        );
    }
}
