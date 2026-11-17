package com.autoparts.erp.ai.security;

import com.autoparts.erp.ai.service.ErpAiAgentService;
import com.autoparts.erp.ai.service.ErpAiGraphNodes;
import com.autoparts.erp.ai.service.ErpAiIntentClassifier;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Asynchronous Security Isolation Tests Through Real Agent Graph Path")
class ErpAiAsyncSecurityIsolationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ChatModel chatModel;

    private ProductLookupTool productLookupTool;
    private InventoryHistoryLookupTool inventoryHistoryLookupTool;
    private ErpAiGraphNodes graphNodes;
    private ErpAiAgentService agentService;

    @BeforeEach
    void setUp() {
        productLookupTool = new ProductLookupTool(jdbcTemplate);
        inventoryHistoryLookupTool = new InventoryHistoryLookupTool(jdbcTemplate);
        ErpAiIntentClassifier intentClassifier = new ErpAiIntentClassifier();

        graphNodes = new ErpAiGraphNodes(
                chatModel,
                productLookupTool,
                intentClassifier,
                inventoryHistoryLookupTool
        );

        agentService = new ErpAiAgentService(graphNodes, Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
        agentService.shutdownTimeoutScheduler();
    }

    @Test
    @DisplayName("End-to-End Graph Path: Cashier is blocked and Manager is permitted via RunnableConfig metadata")
    void shouldPropagateContextThroughRealAgentGraphPathWithoutLeakage() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "movement_uuid", UUID.randomUUID().toString(),
                        "product_code", "GAP-000001",
                        "part_no", "P-001",
                        "product_name", "Brake Pad",
                        "movement_type", "PURCHASE",
                        "quantity_change", 10,
                        "created_at", "2026-11-17T10:00:00Z"
                )));

        UUID convCashier = UUID.randomUUID();
        UUID runCashier = UUID.randomUUID();
        ErpAiExecutionContext cashierCtx = ErpAiExecutionContext.create(
                convCashier, "cashier_user", List.of("ROLE_CASHIER"));

        // Cashier attempts to view movement history through the real agent graph
        String cashierResponse = agentService.chat(
                convCashier,
                runCashier,
                List.of(new UserMessage("Show movement history for GAP-000001")),
                cashierCtx
        );

        assertTrue(cashierResponse.contains("You do not have permission") || cashierResponse.contains("Required role"),
                "Cashier must receive permission denied response: " + cashierResponse);

        // Verify zero JDBC calls for Cashier
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));

        // Manager attempts the same through the real agent graph
        UUID convManager = UUID.randomUUID();
        UUID runManager = UUID.randomUUID();
        ErpAiExecutionContext managerCtx = ErpAiExecutionContext.create(
                convManager, "manager_user", List.of("ROLE_MANAGER"));

        String managerResponse = agentService.chat(
                convManager,
                runManager,
                List.of(new UserMessage("Show movement history for GAP-000001")),
                managerCtx
        );

        assertNotNull(managerResponse);
        assertFalse(managerResponse.contains("You do not have permission"));
        // Manager must trigger JDBC query
        verify(jdbcTemplate, atLeastOnce()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("End-to-End Graph Path: Missing execution context fails closed with access denied and zero JDBC calls")
    void shouldFailClosedWhenGraphExecutionHasNoExecutionContext() {
        UUID convId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        // Chat with null execution context
        String response = agentService.chat(
                convId,
                runId,
                List.of(new UserMessage("Show movement history for GAP-000001")),
                null
        );

        assertTrue(response.contains("No execution context provided")
                        || response.contains("Access denied")
                        || response.contains("You do not have permission"),
                "No-context path must fail closed: " + response);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Concurrent Graph Executions: Zero cross-user context leakage between Cashier and Manager")
    void shouldKeepCashierAndManagerIsolatedUnderConcurrentLoad() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "movement_uuid", UUID.randomUUID().toString(),
                        "product_code", "GAP-000001",
                        "part_no", "P-001",
                        "product_name", "Brake Pad",
                        "movement_type", "PURCHASE",
                        "quantity_change", 5,
                        "created_at", "2026-11-17T10:00:00Z"
                )));

        int taskCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Void>> futures = new ArrayList<>();

        AtomicInteger cashierBlocked = new AtomicInteger(0);
        AtomicInteger managerAllowed = new AtomicInteger(0);

        try {
            for (int i = 0; i < taskCount; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    UUID convId = UUID.randomUUID();
                    UUID runId = UUID.randomUUID();

                    if (idx % 2 == 0) {
                        // Cashier request
                        ErpAiExecutionContext ctx = ErpAiExecutionContext.create(
                                convId, "cashier_" + idx, List.of("ROLE_CASHIER"));

                        String res = agentService.chat(
                                convId,
                                runId,
                                List.of(new UserMessage("Show movement history for GAP-000001")),
                                ctx
                        );

                        if (res.contains("You do not have permission") || res.contains("Required role")) {
                            cashierBlocked.incrementAndGet();
                        }
                    } else {
                        // Manager request
                        ErpAiExecutionContext ctx = ErpAiExecutionContext.create(
                                convId, "manager_" + idx, List.of("ROLE_MANAGER"));

                        String res = agentService.chat(
                                convId,
                                runId,
                                List.of(new UserMessage("Show movement history for GAP-000001")),
                                ctx
                        );

                        if (!res.contains("You do not have permission")) {
                            managerAllowed.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }

            assertEquals(taskCount / 2, cashierBlocked.get(), "All cashier requests must be blocked");
            assertEquals(taskCount / 2, managerAllowed.get(), "All manager requests must be permitted");

        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("ErpAiContextHolder.runWithContext restores previous context and prevents leakage on thread pool reuse")
    void shouldRestoreContextAndPreventLeakageOnReusedThreads() throws Exception {
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

        try {
            UUID convId1 = UUID.randomUUID();
            ErpAiExecutionContext managerCtx = ErpAiExecutionContext.create(
                    convId1, "manager_user", List.of("ROLE_MANAGER"));

            // Run task 1 with manager context
            singleThreadExecutor.submit(() -> {
                ErpAiContextHolder.runWithContext(managerCtx, () -> {
                    assertTrue(ErpAiContextHolder.getContext().isPresent());
                    assertEquals("manager_user", ErpAiContextHolder.getContext().get().userSubject());
                    return null;
                });
                return null;
            }).get(5, TimeUnit.SECONDS);

            // Run task 2 on the SAME thread without context
            singleThreadExecutor.submit(() -> {
                // Must be clean: no residual context on the reused worker thread
                assertTrue(ErpAiContextHolder.getContext().isEmpty(),
                        "Reused thread pool worker must have empty context after runWithContext finishes");
                return null;
            }).get(5, TimeUnit.SECONDS);

        } finally {
            singleThreadExecutor.shutdownNow();
        }
    }
}
