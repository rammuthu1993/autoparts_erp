//package com.autoparts.erp.ai.tools;
//
//import java.util.concurrent.CancellationException;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.jdbc.core.JdbcTemplate;
//
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.Mockito.verifyNoInteractions;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicReference;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class ProductLookupToolTest {
//
//    @Mock
//    private JdbcTemplate jdbcTemplate;
//
//    @InjectMocks
//    private ProductLookupTool productLookupTool;
//
//    @Test
//    void shouldStopExecutionWhenThreadIsInterrupted() {
//
//        Thread.currentThread().interrupt();
//
//        try {
//            assertThrows(
//                    CancellationException.class,
//                    () -> productLookupTool.countProducts()
//            );
//
//            verifyNoInteractions(jdbcTemplate);
//
//        } finally {
//            // Clear interrupt status after the test
//            Thread.interrupted();
//        }
//    }
//    
//
//@Test
//void shouldDemonstrateThatInterruptDoesNotAutomaticallyStopRunningQuery()
//        throws Exception {
//
//    CountDownLatch queryStarted = new CountDownLatch(1);
//    CountDownLatch allowQueryToFinish = new CountDownLatch(1);
//    AtomicReference<Thread> workerThread = new AtomicReference<>();
//
//    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
//            .thenAnswer(invocation -> {
//                queryStarted.countDown();
//
//                // Simulate a JDBC operation that does not respond
//                // immediately to thread interruption.
//                boolean completed = false;
//
//                while (!completed) {
//                    try {
//                        allowQueryToFinish.await();
//                        completed = true;
//                    } catch (InterruptedException exception) {
//                        // Simulate a database operation that ignores
//                        // thread interruption and continues waiting.
//                    }
//                }
//
//                return 10L;
//            });
//
//    ExecutorService executor = Executors.newSingleThreadExecutor();
//
//    try {
//        Future<Long> future = executor.submit(() -> {
//            workerThread.set(Thread.currentThread());
//            return productLookupTool.countProducts();
//        });
//
//        assertTrue(queryStarted.await(2, TimeUnit.SECONDS));
//
//        // Interrupt while the mocked JDBC query is running.
//        workerThread.get().interrupt();
//
//        // The query is still running because it ignores interruption.
//        assertFalse(future.isDone());
//
//        // Allow the simulated query to finish.
//        allowQueryToFinish.countDown();
//
//        assertEquals(10L, future.get(2, TimeUnit.SECONDS));
//
//    } finally {
//        allowQueryToFinish.countDown();
//        executor.shutdownNow();
//    }
//}
//
//}



package com.autoparts.erp.ai.tools;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProductLookupTool productLookupTool;

    @BeforeEach
    void setUp() {
        productLookupTool = new ProductLookupTool(jdbcTemplate);

        // Ignore constructor configuration when verifying query interactions.
        clearInvocations(jdbcTemplate);
    }

    @Test
    void shouldStopExecutionWhenThreadIsInterrupted() {

        Thread.currentThread().interrupt();

        try {
            assertThrows(
                    CancellationException.class,
                    () -> productLookupTool.countProducts()
            );

            verifyNoInteractions(jdbcTemplate);

        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void searchProductsToolUsesSchemaAndReturnsDatabaseRows() {
        var product = java.util.Map.<String, Object>of(
                "uuid", java.util.UUID.randomUUID(),
                "product_code", "PRD001",
                "part_no", "FILTER-001",
                "name", "Oil Filter",
                "qty", 12,
                "unit", "PCS"
        );
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(java.util.List.of(product));

        ToolCallback callback = java.util.Arrays.stream(
                        ToolCallbacks.from(productLookupTool)
                )
                .filter(tool -> tool.getToolDefinition().name().equals("searchProducts"))
                .findFirst()
                .orElseThrow();

        String schema = callback.getToolDefinition().inputSchema();
        assertTrue(schema.contains("searchText"));
        assertTrue(schema.replaceAll("\\s+", "").contains("\"type\":\"string\""));

        String result = callback.call("{\"searchText\":\"PRD001\"}");
        assertTrue(result.contains("\"product_code\":\"PRD001\""));
        assertTrue(result.contains("\"name\":\"Oil Filter\""));
        assertTrue(result.contains("\"qty\":12"));

        var sql = forClass(String.class);
        var args = forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), args.capture());
        String normalizedSql = sql.getValue().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(normalizedSql.contains("where active = true"));
        assertTrue(normalizedSql.contains("lower(product_code) like lower(?)"));
        assertTrue(normalizedSql.contains("lower(part_no) like lower(?)"));
        assertTrue(normalizedSql.contains("lower(name) like lower(?)"));
        assertFalse(normalizedSql.contains("select id,"));
        assertArrayEquals(
                new Object[]{"%PRD001%", "%PRD001%", "%PRD001%"},
                args.getValue()
        );
    }

    @Test
    void searchProductsSurfacesDatabaseFailuresInsteadOfReturningFakeResults() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> productLookupTool.searchProducts("PRD001")
        );

        assertEquals("Unable to search products at this time.", failure.getMessage());
        assertInstanceOf(DataAccessResourceFailureException.class, failure.getCause());
    }

    @Test
    void shouldDemonstrateThatInterruptDoesNotAutomaticallyStopRunningQuery()
            throws Exception {

        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        CountDownLatch allowQueryToFinish = new CountDownLatch(1);

        AtomicReference<Thread> workerThread = new AtomicReference<>();

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenAnswer(invocation -> {

                    queryStarted.countDown();

                    boolean completed = false;

                    while (!completed) {
                        try {
                            allowQueryToFinish.await();
                            completed = true;

                        } catch (InterruptedException exception) {

                            // Simulate a database operation that
                            // does not stop when its thread is interrupted.
                            interruptionObserved.countDown();
                        }
                    }

                    return 10L;
                });

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {
            Future<Long> future = executor.submit(() -> {

                workerThread.set(Thread.currentThread());

                return productLookupTool.countProducts();
            });

            assertTrue(
                    queryStarted.await(2, TimeUnit.SECONDS),
                    "Database query should start"
            );

            workerThread.get().interrupt();

            assertTrue(
                    interruptionObserved.await(2, TimeUnit.SECONDS),
                    "Worker should observe the interruption"
            );

            assertFalse(
                    future.isDone(),
                    "Simulated query should continue despite interruption"
            );

            allowQueryToFinish.countDown();

            assertEquals(
                    10L,
                    future.get(2, TimeUnit.SECONDS)
            );

        } finally {
            allowQueryToFinish.countDown();
            executor.shutdownNow();
        }
    }
}
