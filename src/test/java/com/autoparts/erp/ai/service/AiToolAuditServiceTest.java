package com.autoparts.erp.ai.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autoparts.erp.ai.repository.AiRepository;
import com.autoparts.erp.ai.exception.AiToolAuditPersistenceException;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

import tools.jackson.databind.ObjectMapper;

class AiToolAuditServiceTest {

    @Test
    void persistsStructuredMetadataWithoutRawSearchTextOrResultRows() {
        AiRepository repository = mock(AiRepository.class);
        AiToolAuditService service = new AiToolAuditService(
                repository, new ObjectMapper(), new AiMetricsService());
        UUID conversationId = UUID.randomUUID();
        ErpAiExecutionContext context = ErpAiExecutionContext.create(
                conversationId, "admin-user", List.of("ROLE_ADMIN"));

        service.record(
                context,
                "PRODUCT_SEARCH",
                "sensitive customer/product search phrase",
                "",
                "",
                10,
                List.of(Map.of("product_id", 1, "name", "private result")),
                "SUCCESS",
                null,
                37);

        ArgumentCaptor<String> arguments = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> result = ArgumentCaptor.forClass(String.class);

        verify(repository).saveToolRun(
                eq(conversationId),
                eq("admin-user"),
                eq("PRODUCT_SEARCH"),
                arguments.capture(),
                result.capture(),
                eq("SUCCESS"),
                isNull(),
                eq(37L),
                eq(1));

        org.junit.jupiter.api.Assertions.assertFalse(
                arguments.getValue().contains("sensitive customer/product search phrase"));
        org.junit.jupiter.api.Assertions.assertTrue(
                arguments.getValue().contains("searchTextSha256"));
        org.junit.jupiter.api.Assertions.assertFalse(
                result.getValue().contains("private result"));
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getValue().contains("rowsReturned"));
    }

    @Test
    void auditPersistenceFailureDoesNotMaskToolResponsePathInBestEffortMode() {
        AiRepository repository = mock(AiRepository.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).saveToolRun(
                        any(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), any(), anyLong(), anyInt());

        AiToolAuditService service = new AiToolAuditService(
                repository, new ObjectMapper(), new AiMetricsService(), false);
        ErpAiExecutionContext context = ErpAiExecutionContext.create(
                UUID.randomUUID(), "admin-user", List.of("ROLE_ADMIN"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                service.record(context, "PRODUCT_SEARCH", "part-123", "", "",
                        10, List.of(Map.of("id", 1)), "SUCCESS", null, 5));
    }

    @Test
    void auditPersistenceFailureFailsClosedWhenConfiguredForProduction() {
        AiRepository repository = mock(AiRepository.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).saveToolRun(
                        any(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), any(), anyLong(), anyInt());

        AiToolAuditService service = new AiToolAuditService(
                repository, new ObjectMapper(), new AiMetricsService(), true);
        ErpAiExecutionContext context = ErpAiExecutionContext.create(
                UUID.randomUUID(), "admin-user", List.of("ROLE_ADMIN"));

        org.junit.jupiter.api.Assertions.assertThrows(AiToolAuditPersistenceException.class,
                () -> service.record(context, "PRODUCT_SEARCH", "part-123", "", "",
                        10, List.of(Map.of("id", 1)), "SUCCESS", null, 5));
    }

    @Test
    void missingExecutionContextFailsClosedInProductionMode() {
        AiRepository repository = mock(AiRepository.class);
        AiToolAuditService service = new AiToolAuditService(
                repository, new ObjectMapper(), new AiMetricsService(), true);

        org.junit.jupiter.api.Assertions.assertThrows(AiToolAuditPersistenceException.class,
                () -> service.record(null, "PRODUCT_SEARCH", "part-123", "", "",
                        10, List.of(), "SUCCESS", null, 1));
        verifyNoInteractions(repository);
    }
}
