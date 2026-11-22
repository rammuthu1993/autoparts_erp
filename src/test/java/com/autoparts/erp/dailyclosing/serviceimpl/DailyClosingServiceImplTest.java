package com.autoparts.erp.dailyclosing.serviceimpl;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.dto.DailyClosingSummary;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Daily Closing Service Concurrency & Correctness Tests")
class DailyClosingServiceImplTest {

    private DailyClosingRepository repository;
    private CurrentUserService currentUserService;
    private AuditLogService auditLogService;
    private DailyClosingServiceImpl service;

    private static final String TIME_ZONE = "Asia/Kolkata";

    @BeforeEach
    void setUp() {
        repository = mock(DailyClosingRepository.class);
        currentUserService = mock(CurrentUserService.class);
        auditLogService = mock(AuditLogService.class);

        when(currentUserService.getCurrentUserId()).thenReturn(1L);

        service = new DailyClosingServiceImpl(
                repository,
                currentUserService,
                auditLogService,
                TIME_ZONE
        );
    }

    private DailyClosingResponse createMockResponse(LocalDate date) {
        DailyClosingSummary summary = new DailyClosingSummary(
                5L, new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0L, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                2L, new BigDecimal("400.00"),
                new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                1L, new BigDecimal("50.00"),
                new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                1L, new BigDecimal("100.00"), new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("600.00"), new BigDecimal("600.00"), BigDecimal.ZERO
        );

        return new DailyClosingResponse(
                UUID.randomUUID(),
                date,
                1L,
                OffsetDateTime.now(),
                "End of day closing",
                summary,
                List.of()
        );
    }

    @Test
    @DisplayName("Successfully closes business date when all conditions are met and logs audit record")
    void shouldSuccessfullyCloseBusinessDate() {
        LocalDate date = LocalDate.now().minusDays(1);
        when(repository.existsByBusinessDate(date)).thenReturn(false);
        when(repository.hasOpenSessions(any(), any())).thenReturn(false);

        DailyClosingResponse mockResponse = createMockResponse(date);
        when(repository.createClosing(eq(date), any(), any(), eq(1L), eq("Notes"))).thenReturn(mockResponse);

        DailyClosingResponse response = service.closeBusinessDate(date, "Notes");

        assertNotNull(response);
        assertEquals(date, response.businessDate());
        verify(repository).createClosing(eq(date), any(), any(), eq(1L), eq("Notes"));
        verify(auditLogService).log(
                eq("CREATE"),
                eq("DAILY_CLOSING"),
                eq(date.toString()),
                anyString(),
                isNull(),
                anyMap()
        );
    }

    @Test
    @DisplayName("Rejects null business date with 400 Bad Request")
    void shouldRejectNullBusinessDate() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.closeBusinessDate(null, "Notes")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("required"));
    }

    @Test
    @DisplayName("Rejects future business date with 400 Bad Request")
    void shouldRejectFutureBusinessDate() {
        LocalDate futureDate = LocalDate.now().plusDays(2);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.closeBusinessDate(futureDate, "Notes")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Future"));
    }

    @Test
    @DisplayName("Rejects daily closing when open cash register sessions still exist")
    void shouldRejectWhenOpenCashRegisterSessionsExist() {
        LocalDate date = LocalDate.now().minusDays(1);
        when(repository.existsByBusinessDate(date)).thenReturn(false);
        when(repository.hasOpenSessions(any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.closeBusinessDate(date, "Notes")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Close all cash register sessions"));
        verify(repository, never()).createClosing(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rejects when daily closing already exists via pre-check (409 Conflict)")
    void shouldRejectWhenDailyClosingAlreadyExistsPreCheck() {
        LocalDate date = LocalDate.now().minusDays(1);
        when(repository.existsByBusinessDate(date)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.closeBusinessDate(date, "Notes")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists"));
        verify(repository, never()).createClosing(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Translates PostgreSQL unique constraint DuplicateKeyException to 409 Conflict under race condition")
    void shouldHandleDuplicateKeyExceptionFromDatabase() {
        LocalDate date = LocalDate.now().minusDays(1);
        // Pre-check passes
        when(repository.existsByBusinessDate(date)).thenReturn(false);
        when(repository.hasOpenSessions(any(), any())).thenReturn(false);
        // Database throws DuplicateKeyException due to concurrent duplicate insertion
        when(repository.createClosing(eq(date), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint idx_daily_closings_business_date"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.closeBusinessDate(date, "Notes")
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists for this date"));
    }

    @Test
    @DisplayName("Thread-safety: Concurrent closing requests guarantee exactly 1 success and 409 Conflict for loser")
    void shouldHandleConcurrentClosingRequestsSafely() throws InterruptedException, ExecutionException {
        LocalDate date = LocalDate.now().minusDays(1);
        DailyClosingResponse mockResponse = createMockResponse(date);

        when(repository.existsByBusinessDate(date)).thenReturn(false);
        when(repository.hasOpenSessions(any(), any())).thenReturn(false);

        // Atomic simulate: first caller succeeds, subsequent concurrent callers throw DuplicateKeyException
        AtomicInteger invocationCount = new AtomicInteger(0);
        when(repository.createClosing(eq(date), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (invocationCount.incrementAndGet() == 1) {
                        return mockResponse;
                    }
                    throw new DuplicateKeyException("duplicate key value violates unique constraint");
                });

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    DailyClosingResponse res = service.closeBusinessDate(date, "Concurrent");
                    if (res != null) {
                        successCount.incrementAndGet();
                    }
                } catch (ResponseStatusException rse) {
                    if (rse.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one concurrent thread must succeed");
        assertEquals(threads - 1, conflictCount.get(), "All losing threads must receive 409 Conflict");
    }

    @Test
    @DisplayName("Retrieves closing by business date or throws 404 Not Found")
    void shouldFindClosingByBusinessDate() {
        LocalDate date = LocalDate.now().minusDays(1);
        DailyClosingResponse mockResponse = createMockResponse(date);

        when(repository.findByBusinessDate(date)).thenReturn(Optional.of(mockResponse));
        DailyClosingResponse found = service.findByBusinessDate(date);
        assertNotNull(found);
        assertEquals(date, found.businessDate());

        when(repository.findByBusinessDate(date)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.findByBusinessDate(date)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
