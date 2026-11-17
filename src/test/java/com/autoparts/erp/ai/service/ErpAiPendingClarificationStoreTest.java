package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErpAiPendingClarificationStoreTest {

    private ErpAiPendingClarificationStore store;

    @BeforeEach
    void setUp() {
        store = new ErpAiPendingClarificationStore();
    }

    @Test
    @DisplayName("Should successfully register and retrieve pending slot for matching user and conversation")
    void shouldRegisterAndRetrieveForMatchingUser() {
        UUID conversationId = UUID.randomUUID();
        String user = "user-alice";

        store.register(conversationId, user, "DAILY_CLOSING", "startDate", Map.of("key", "val"));

        var result = store.findAndValidate(conversationId, user);
        assertTrue(result.isPresent());
        assertEquals("DAILY_CLOSING", result.get().pendingIntent());
        assertEquals("startDate", result.get().pendingSlotName());
        assertEquals("user-alice", result.get().userSubject());
        assertEquals(conversationId, result.get().conversationId());
    }

    @Test
    @DisplayName("Should reject retrieval if userSubject does not match registered owner")
    void shouldRejectRetrievalForMismatchedUser() {
        UUID conversationId = UUID.randomUUID();
        String owner = "user-alice";
        String attacker = "user-bob";

        store.register(conversationId, owner, "DAILY_CLOSING", "startDate", Map.of());

        var result = store.findAndValidate(conversationId, attacker);
        assertTrue(result.isEmpty(), "Cross-user retrieval must return empty");
    }

    @Test
    @DisplayName("Should prevent user B from overwriting user A's pending clarification")
    void shouldPreventCrossUserOverwrite() {
        UUID conversationId = UUID.randomUUID();
        String owner = "user-alice";
        String attacker = "user-bob";

        store.register(conversationId, owner, "DAILY_CLOSING", "startDate", Map.of());

        // Attacker attempts to overwrite with a different intent
        store.register(conversationId, attacker, "PURCHASE_SEARCH", "searchText", Map.of());

        // Owner's original intent must be preserved untouched
        var result = store.findAndValidate(conversationId, owner);
        assertTrue(result.isPresent());
        assertEquals("DAILY_CLOSING", result.get().pendingIntent());

        // Attacker cannot read the slot
        assertTrue(store.findAndValidate(conversationId, attacker).isEmpty());
    }

    @Test
    @DisplayName("Should prevent user B from removing user A's pending clarification")
    void shouldPreventCrossUserRemoval() {
        UUID conversationId = UUID.randomUUID();
        String owner = "user-alice";
        String attacker = "user-bob";

        store.register(conversationId, owner, "PRODUCT_STOCK", "productCode", Map.of());

        // Attacker's removal attempt must return false and not delete the slot
        assertFalse(store.remove(conversationId, attacker));
        assertTrue(store.findAndValidate(conversationId, owner).isPresent());

        // Owner removal succeeds
        assertTrue(store.remove(conversationId, owner));
        assertTrue(store.findAndValidate(conversationId, owner).isEmpty());
    }

    @Test
    @DisplayName("Should expire slots after configured TTL and prune during cleanup")
    void shouldHandleTtlExpiration() throws InterruptedException {
        // Create store with 1 second TTL
        ErpAiPendingClarificationStore shortTtlStore = new ErpAiPendingClarificationStore(100, 1);
        UUID conversationId = UUID.randomUUID();
        String user = "user-alice";

        shortTtlStore.register(conversationId, user, "DAILY_CLOSING", "startDate", Map.of());
        assertTrue(shortTtlStore.findAndValidate(conversationId, user).isPresent());

        // Wait for TTL to expire
        Thread.sleep(1100);

        // findAndValidate removes and returns empty
        assertTrue(shortTtlStore.findAndValidate(conversationId, user).isEmpty());
        assertEquals(0, shortTtlStore.size());
    }

    @Test
    @DisplayName("Should strictly enforce maximum capacity and evict oldest entries in FIFO order")
    void shouldStrictlyEnforceCapacityAndEvictOldest() {
        // Capacity = 3
        ErpAiPendingClarificationStore boundedStore = new ErpAiPendingClarificationStore(3, 900);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();

        boundedStore.register(id1, "user1", "DAILY_CLOSING", "startDate", Map.of());
        boundedStore.register(id2, "user2", "DAILY_CLOSING", "startDate", Map.of());
        boundedStore.register(id3, "user3", "DAILY_CLOSING", "startDate", Map.of());

        assertEquals(3, boundedStore.size());

        // Register 4th entry -> id1 (oldest) must be evicted
        boundedStore.register(id4, "user4", "DAILY_CLOSING", "startDate", Map.of());

        assertEquals(3, boundedStore.size(), "Size must never exceed maxCapacity");
        assertTrue(boundedStore.findAndValidate(id1, "user1").isEmpty(), "Oldest entry must be evicted");
        assertTrue(boundedStore.findAndValidate(id2, "user2").isPresent());
        assertTrue(boundedStore.findAndValidate(id3, "user3").isPresent());
        assertTrue(boundedStore.findAndValidate(id4, "user4").isPresent());
    }

    @Test
    @DisplayName("Should guarantee maximum capacity under high concurrent registration")
    void shouldGuaranteeCapacityUnderConcurrentLoad() throws Exception {
        int capacity = 20;
        ErpAiPendingClarificationStore boundedStore = new ErpAiPendingClarificationStore(capacity, 900);

        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final String user = "user-" + t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                for (int i = 0; i < operationsPerThread; i++) {
                    boundedStore.register(UUID.randomUUID(), user, "PRODUCT_STOCK", "productCode", Map.of());
                }
                return null;
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Size must strictly be <= capacity
        assertTrue(boundedStore.size() <= capacity,
                "Store size (" + boundedStore.size() + ") must never exceed capacity (" + capacity + ")");
        assertEquals(capacity, boundedStore.size());
    }

    @Test
    @DisplayName("Updating an existing same-owner entry at full capacity must not evict other users' entries")
    void shouldUpdateExistingEntryAtFullCapacityWithoutEvictingOtherEntries() {
        // Store with capacity 3
        ErpAiPendingClarificationStore boundedStore = new ErpAiPendingClarificationStore(3, 900);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        boundedStore.register(id1, "user1", "DAILY_CLOSING", "startDate", Map.of("step", 1));
        boundedStore.register(id2, "user2", "PURCHASE_SEARCH", "searchText", Map.of());
        boundedStore.register(id3, "user3", "SALES_SEARCH", "searchText", Map.of());

        assertEquals(3, boundedStore.size());

        // User 1 updates id1 at full capacity
        boundedStore.register(id1, "user1", "DAILY_CLOSING", "startDate", Map.of("step", 2));

        // Size must still be exactly 3
        assertEquals(3, boundedStore.size());

        // Verify id1 has updated data
        var updated = boundedStore.findAndValidate(id1, "user1");
        assertTrue(updated.isPresent());
        assertEquals(2, updated.get().partialParameters().get("step"));

        // Crucially, verify id2 (oldest other entry) and id3 were NOT evicted
        assertTrue(boundedStore.findAndValidate(id2, "user2").isPresent(), "user2's entry must not be evicted");
        assertTrue(boundedStore.findAndValidate(id3, "user3").isPresent(), "user3's entry must not be evicted");
    }

    @Test
    @DisplayName("Should clean all expired entries via explicit cleanExpired and lazy findAndValidate")
    void shouldCleanExpiredEntriesViaExplicitAndLazyCleanup() throws InterruptedException {
        ErpAiPendingClarificationStore shortTtlStore = new ErpAiPendingClarificationStore(10, 1);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        shortTtlStore.register(id1, "user1", "DAILY_CLOSING", "startDate", Map.of());
        shortTtlStore.register(id2, "user1", "DAILY_CLOSING", "startDate", Map.of());

        assertEquals(2, shortTtlStore.size());

        Thread.sleep(1100);

        // Explicit cleanup purges all expired entries
        shortTtlStore.cleanExpired();
        assertEquals(0, shortTtlStore.size());

        // Test lazy cleanup during findAndValidate
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();
        shortTtlStore.register(id3, "user2", "PRODUCT_STOCK", "productCode", Map.of());
        shortTtlStore.register(id4, "user2", "PRODUCT_STOCK", "productCode", Map.of());
        assertEquals(2, shortTtlStore.size());

        Thread.sleep(1100);

        // findAndValidate triggers lazy cleanExpiredInternal
        assertTrue(shortTtlStore.findAndValidate(UUID.randomUUID(), "user2").isEmpty());
        assertEquals(0, shortTtlStore.size(), "Lazy cleanup in findAndValidate must purge all expired entries");
    }
}
