package com.autoparts.erp.dailyclosing.repositoryimpl;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.serviceimpl.DailyClosingServiceImpl;
import com.autoparts.erp.repository.BaseRepositoryIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Daily Closing Real PostgreSQL Concurrency Integration Test")
class DailyClosingPostgresConcurrencyIntegrationTest extends BaseRepositoryIntegrationTest {

    private DailyClosingRepositoryImpl repository;
    private CurrentUserService currentUserService;
    private AuditLogService auditLogService;
    private DailyClosingServiceImpl service;

    private Long testUserId;
    private LocalDate testBusinessDate;
    private final java.util.concurrent.atomic.AtomicReference<Long> createdClosingId = new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicReference<UUID> createdClosingUuid = new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = new DailyClosingRepositoryImpl(getJdbcTemplate());
        currentUserService = mock(CurrentUserService.class);
        auditLogService = mock(AuditLogService.class);

        // Ensure a valid user exists for foreign key
        List<Long> userIds = getJdbcTemplate().getJdbcTemplate().query(
                "SELECT id FROM users WHERE enabled = TRUE ORDER BY id ASC LIMIT 1",
                (rs, rowNum) -> rs.getLong("id")
        );
        if (!userIds.isEmpty()) {
            testUserId = userIds.get(0);
        } else {
            UUID userUuid = UUID.randomUUID();
            testUserId = getJdbcTemplate().getJdbcTemplate().queryForObject(
                    "INSERT INTO users (uuid, username, password_hash, enabled) VALUES (?, ?, ?, true) RETURNING id",
                    Long.class,
                    userUuid, "closer_" + userUuid.toString().substring(0, 8), "secret"
            );
        }
        when(currentUserService.getCurrentUserId()).thenReturn(testUserId);

        service = new DailyClosingServiceImpl(
                repository,
                currentUserService,
                auditLogService,
                "Asia/Kolkata"
        );

        // Non-destructive: Dynamically allocate a guaranteed unused historical business date
        testBusinessDate = generateUniqueBusinessDate();
    }

    @AfterEach
    void tearDown() {
        // Safely clean up ONLY the exact record created by the successful worker,
        // identified by its primary key (daily_closings.id). Never delete by business_date alone.
        Long id = createdClosingId.get();
        if (id == null && createdClosingUuid.get() != null) {
            List<Long> ids = getJdbcTemplate().query(
                    "SELECT id FROM daily_closings WHERE uuid = :uuid",
                    Map.of("uuid", createdClosingUuid.get()),
                    (rs, rowNum) -> rs.getLong("id")
            );
            if (!ids.isEmpty()) {
                id = ids.get(0);
                createdClosingId.set(id);
            }
        }
        if (id != null) {
            getJdbcTemplate().update(
                    "DELETE FROM daily_closing_cash_registers WHERE daily_closing_id = :id",
                    Map.of("id", id)
            );
            getJdbcTemplate().update(
                    "DELETE FROM daily_closings WHERE id = :id",
                    Map.of("id", id)
            );
        }
    }

    private LocalDate generateUniqueBusinessDate() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 50; attempt++) {
            // Allocate a date between 12 and 30 years ago (e.g. 1996 - 2014)
            int yearsAgo = 12 + random.nextInt(18);
            int dayOfYear = 1 + random.nextInt(350);
            LocalDate candidate = LocalDate.ofYearDay(LocalDate.now().getYear() - yearsAgo, dayOfYear);

            // Verify non-existence in database to preserve all pre-existing records
            if (!repository.existsByBusinessDate(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to find an unused historical business date after 50 attempts");
    }

    @Test
    @DisplayName("High concurrency: Multiple threads attempt concurrent closeBusinessDate against real PostgreSQL; exactly 1 succeeds and others get 409 Conflict")
    void testConcurrentDailyClosingAgainstRealPostgres() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrors = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = new CopyOnWriteArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        // Each concurrent thread invokes the closing service inside its own transaction
                        DailyClosingResponse response = getTransactionTemplate().execute(status ->
                                service.closeBusinessDate(testBusinessDate, "Concurrent thread " + index)
                        );
                        if (response != null) {
                            successCount.incrementAndGet();
                            createdClosingUuid.set(response.uuid());
                        }
                    } catch (ResponseStatusException rse) {
                        if (rse.getStatusCode() == HttpStatus.CONFLICT) {
                            conflictCount.incrementAndGet();
                        } else {
                            unexpectedErrors.incrementAndGet();
                            unexpectedExceptions.add(rse);
                        }
                    } catch (Throwable t) {
                        Throwable root = t;
                        while (root.getCause() != null) {
                            root = root.getCause();
                        }
                        if (root instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.CONFLICT) {
                            conflictCount.incrementAndGet();
                        } else {
                            unexpectedErrors.incrementAndGet();
                            unexpectedExceptions.add(t);
                        }
                    }
                });
            }

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS), "All threads must be ready");
            startLatch.countDown(); // Fire all threads simultaneously

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "All threads must finish execution");

            assertEquals(0, unexpectedErrors.get(),
                    "No unexpected errors should occur during concurrent close: " + unexpectedExceptions);
            assertEquals(1, successCount.get(),
                    "Exactly one concurrent thread must succeed in closing the business date");
            assertEquals(threadCount - 1, conflictCount.get(),
                    "All " + (threadCount - 1) + " losing threads must receive 409 Conflict via database constraint");

            // Safely capture the primary key (id) of the record created by the successful worker
            assertNotNull(createdClosingUuid.get(), "Successful worker must have recorded closing UUID");
            Long id = getJdbcTemplate().queryForObject(
                    "SELECT id FROM daily_closings WHERE uuid = :uuid",
                    Map.of("uuid", createdClosingUuid.get()),
                    Long.class
            );
            assertNotNull(id, "Created closing primary key (id) must exist in database");
            createdClosingId.set(id);

            // Verify the database state directly
            Long closingCount = getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM daily_closings WHERE business_date = :bDate",
                    Map.of("bDate", testBusinessDate),
                    Long.class
            );
            assertEquals(1L, closingCount, "Exactly one row must exist in daily_closings table");

            // Verify that the created closing is retrievable
            assertTrue(repository.existsByBusinessDate(testBusinessDate));
            assertTrue(repository.findByBusinessDate(testBusinessDate).isPresent());

        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void previewBusinessDateExecutesAgainstRealPostgres() {
        var preview = service.previewBusinessDate(testBusinessDate);
        assertNotNull(preview);
        assertEquals(testBusinessDate, preview.businessDate());
        assertNotNull(preview.summary());
        assertNotNull(preview.cashRegisters());
        assertNotNull(preview.validationErrors());
    }
}
