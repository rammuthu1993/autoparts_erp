package com.autoparts.erp.ai.rag.retrieval;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KnowledgeAccessControlTest {

    private KnowledgeRetrievalEngine mockEngine;
    private KnowledgeRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        mockEngine = mock(KnowledgeRetrievalEngine.class);
        retrievalService = new KnowledgeRetrievalService(mockEngine);
    }

    @Test
    @DisplayName("Role hierarchy matrix correctly resolves allowed document roles")
    void shouldResolveRoleHierarchyMatrix() {
        // ADMIN gets all roles
        Set<String> adminRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("ROLE_ADMIN"));
        assertTrue(adminRoles.containsAll(Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_CASHIER", "PUBLIC")));

        // MANAGER gets MANAGER, CASHIER, PUBLIC
        Set<String> managerRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("ROLE_MANAGER"));
        assertEquals(Set.of("ROLE_MANAGER", "ROLE_CASHIER", "PUBLIC"), managerRoles);
        assertFalse(managerRoles.contains("ROLE_ADMIN"));

        // CASHIER gets CASHIER, PUBLIC
        Set<String> cashierRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("ROLE_CASHIER"));
        assertEquals(Set.of("ROLE_CASHIER", "PUBLIC"), cashierRoles);
        assertFalse(cashierRoles.contains("ROLE_ADMIN"));
        assertFalse(cashierRoles.contains("ROLE_MANAGER"));

        // General authenticated user gets only PUBLIC
        Set<String> viewerRoles = KnowledgeRetrievalService.resolveAllowedRoles(Set.of("ROLE_USER"));
        assertEquals(Set.of("PUBLIC"), viewerRoles);

        // Null or empty returns empty
        assertTrue(KnowledgeRetrievalService.resolveAllowedRoles(null).isEmpty());
        assertTrue(KnowledgeRetrievalService.resolveAllowedRoles(Set.of()).isEmpty());
    }

    @Test
    @DisplayName("KnowledgeRetrievalService propagates context authorities to engine pre-filtering")
    @SuppressWarnings("unchecked")
    void shouldPassResolvedRolesToRetrievalEngine() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext cashierContext = ErpAiExecutionContext.create(
                conversationId,
                "cashier_user",
                Set.of("ROLE_CASHIER")
        );

        ArgumentCaptor<Set<String>> roleCaptor = ArgumentCaptor.forClass(Set.class);
        when(mockEngine.retrieve(anyString(), roleCaptor.capture(), anyInt())).thenReturn(List.of());

        retrievalService.retrieveKnowledge("return policy", cashierContext, 4);

        Set<String> passedRoles = roleCaptor.getValue();
        assertNotNull(passedRoles);
        assertEquals(Set.of("ROLE_CASHIER", "PUBLIC"), passedRoles);
        assertFalse(passedRoles.contains("ROLE_ADMIN"), "Cashier must never have ROLE_ADMIN in SQL query filter");
        assertFalse(passedRoles.contains("ROLE_MANAGER"), "Cashier must never have ROLE_MANAGER in SQL query filter");
    }

    @Test
    @DisplayName("Missing or unauthenticated execution context fails closed with ToolAuthorizationException")
    void shouldFailClosedWhenContextIsMissingOrUnauthenticated() {
        assertThrows(ToolAuthorizationException.class, () ->
                retrievalService.retrieveKnowledge("pricing formulas", null)
        );

        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext emptyAuthoritiesContext = ErpAiExecutionContext.create(
                conversationId,
                "anonymous",
                Set.of()
        );

        assertThrows(ToolAuthorizationException.class, () ->
                retrievalService.retrieveKnowledge("pricing formulas", emptyAuthoritiesContext)
        );

        verify(mockEngine, never()).retrieve(anyString(), anySet(), anyInt());
    }

    @Test
    @DisplayName("SQL pre-filtering guarantees unpermitted documents cannot be retrieved")
    void shouldGuaranteeUnpermittedDocumentsAreExcluded() {
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext managerContext = ErpAiExecutionContext.create(
                conversationId,
                "manager_user",
                Set.of("ROLE_MANAGER")
        );

        RetrievedChunk managerChunk = new RetrievedChunk(
                1L, UUID.randomUUID(), "MAN-01", "Manager Guide", 1, "Discounts", "Manager discounts up to 20%", 10, 0.9
        );

        when(mockEngine.retrieve(eq("discounts"), eq(Set.of("ROLE_MANAGER", "ROLE_CASHIER", "PUBLIC")), eq(4)))
                .thenReturn(List.of(managerChunk));

        List<RetrievedChunk> results = retrievalService.retrieveKnowledge("discounts", managerContext, 4);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("MAN-01", results.getFirst().documentCode());

        // Verify that ROLE_ADMIN was not sent to retrieval engine
        verify(mockEngine).retrieve(eq("discounts"), argThat(roles -> !roles.contains("ROLE_ADMIN")), eq(4));
    }
}
