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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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
