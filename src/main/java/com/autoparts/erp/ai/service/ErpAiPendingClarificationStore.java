package com.autoparts.erp.ai.service;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thread-safe, strictly bounded in-memory store for preserving multi-turn clarification slots
 * across separate HTTP requests for the same authenticated user and conversation.
 *
 * <p><strong>Architectural and Security Guarantees:</strong>
 * <ul>
 *   <li><strong>User Isolation & Ownership:</strong> Every pending clarification slot is strictly bound
 *       to both {@code conversationId} and the authenticated {@code userSubject}. A pending
 *       state registered by User A can never be retrieved, inspected, resolved, or removed by User B.
 *       User B cannot overwrite User A's pending state.</li>
 *   <li><strong>Single-Instance Scope:</strong> Per architectural decision DEF-003, this store
 *       is an in-memory component local to this application instance. In-memory state is lost
 *       upon application restart and is not shared across multi-instance clusters.</li>
 *   <li><strong>Strict Capacity & Deterministic FIFO Eviction:</strong> Capped at {@link #MAX_CAPACITY}
 *       entries. When capacity is reached under concurrent load, expired entries are cleaned first.
 *       When inserting a new conversation entry at full capacity, the oldest registered entries are evicted
 *       in strict FIFO order. Updating an existing entry belonging to the same owner never evicts another
 *       user's valid entry.</li>
 *   <li><strong>TTL & Lazy Continuous Cleanup:</strong> Entries older than {@link #DEFAULT_TTL_SECONDS} (15 minutes)
 *       expire automatically. Thread-safe lazy cleanup is performed automatically on every write
 *       ({@link #register}) and read ({@link #findAndValidate}) operation, ensuring expired entries are
 *       promptly purged without requiring a background scheduler thread. A public {@link #cleanExpired()}
 *       method is also provided for scheduled or explicit maintenance.</li>
 *   <li><strong>No Fragile String Parsing:</strong> The store uses explicit structured state records.
 *       It deliberately does not attempt to parse arbitrary natural language from previous assistant messages.</li>
 * </ul>
 * </p>
 */
@Component
public class ErpAiPendingClarificationStore {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiPendingClarificationStore.class);

    public static final int MAX_CAPACITY = 5000;
    public static final long DEFAULT_TTL_SECONDS = 900; // 15 minutes

    public record PendingSlot(
            UUID conversationId,
            String userSubject,
            String pendingIntent,
            String pendingSlotName,
            Map<String, Object> partialParameters,
            Instant createdAt
    ) {
        public boolean isExpired(Instant now, long ttlSeconds) {
            return now.isAfter(createdAt.plusSeconds(ttlSeconds));
        }
    }

    private final int maxCapacity;
    private final long ttlSeconds;
    private final Map<UUID, PendingSlot> store;
    private final Object lock = new Object();

    public ErpAiPendingClarificationStore() {
        this(MAX_CAPACITY, DEFAULT_TTL_SECONDS);
    }

    public ErpAiPendingClarificationStore(int maxCapacity, long ttlSeconds) {
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("maxCapacity must be at least 1");
        }
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("ttlSeconds must be at least 1");
        }
        this.maxCapacity = maxCapacity;
        this.ttlSeconds = ttlSeconds;
        this.store = new LinkedHashMap<>();
    }

    public void register(
            UUID conversationId,
            String userSubject,
            String intent,
            String slotName,
            Map<String, Object> params
    ) {
        if (conversationId == null || userSubject == null || intent == null || slotName == null) {
            return;
        }

        synchronized (lock) {
            cleanExpiredInternal();

            PendingSlot existing = store.get(conversationId);
            // Verify ownership: User B cannot overwrite User A's unexpired slot
            if (existing != null && !existing.isExpired(Instant.now(), ttlSeconds)) {
                if (!existing.userSubject().equals(userSubject)) {
                    log.warn(
                            "Security violation: userSubject '{}' attempted to overwrite pending clarification for conversation '{}' owned by '{}'",
                            userSubject, conversationId, existing.userSubject()
                    );
                    return;
                }
            }

            // Only evict the oldest entry if inserting a NEW conversation and already at maxCapacity
            if (existing == null && store.size() >= maxCapacity) {
                while (store.size() >= maxCapacity) {
                    Iterator<UUID> iterator = store.keySet().iterator();
                    if (iterator.hasNext()) {
                        UUID oldestKey = iterator.next();
                        iterator.remove();
                        log.debug("Evicted oldest pending clarification key '{}' to respect MAX_CAPACITY ({})", oldestKey, maxCapacity);
                    } else {
                        break;
                    }
                }
            }

            store.put(conversationId, new PendingSlot(
                    conversationId,
                    userSubject,
                    intent,
                    slotName,
                    params != null ? Map.copyOf(params) : Map.of(),
                    Instant.now()
            ));

            log.debug("Registered pending clarification for conversation '{}', user '{}', intent '{}', slot '{}'",
                    conversationId, userSubject, intent, slotName);
        }
    }

    public Optional<PendingSlot> findAndValidate(
            UUID conversationId,
            String userSubject
    ) {
        if (conversationId == null || userSubject == null) {
            return Optional.empty();
        }

        synchronized (lock) {
            cleanExpiredInternal();

            PendingSlot slot = store.get(conversationId);
            if (slot == null) {
                return Optional.empty();
            }

            // Check expiration (in case just expired)
            if (slot.isExpired(Instant.now(), ttlSeconds)) {
                store.remove(conversationId);
                return Optional.empty();
            }

            // Verify conversation ownership: userSubject MUST match
            if (!userSubject.equals(slot.userSubject())) {
                log.warn(
                        "Security violation: userSubject '{}' attempted to access pending clarification for conversation '{}' owned by '{}'",
                        userSubject, conversationId, slot.userSubject()
                );
                return Optional.empty();
            }

            return Optional.of(slot);
        }
    }

    public boolean remove(UUID conversationId, String userSubject) {
        if (conversationId == null || userSubject == null) {
            return false;
        }
        synchronized (lock) {
            PendingSlot slot = store.get(conversationId);
            if (slot != null && userSubject.equals(slot.userSubject())) {
                store.remove(conversationId);
                return true;
            }
            return false;
        }
    }

    public void cleanExpired() {
        synchronized (lock) {
            cleanExpiredInternal();
        }
    }

    private void cleanExpiredInternal() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry -> entry.getValue().isExpired(now, ttlSeconds));
    }

    public int size() {
        synchronized (lock) {
            return store.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            store.clear();
        }
    }
}
