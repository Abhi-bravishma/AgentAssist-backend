package com.agentassist.service.checklist;

import com.agentassist.service.checklist.ChecklistService.ChecklistContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache service for storing checklist context per interaction.
 * Caches checklist data to avoid repeated API calls and detection within the same conversation.
 */
@Slf4j
@Service
public class ChecklistCacheService {

    // Cache TTL in seconds (30 minutes)
    private static final long CACHE_TTL_SECONDS = 1800;

    /**
     * Hard ceiling on entries. Expiry alone does not bound this map: an entry is
     * only removed when someone reads it again, and a finished conversation is
     * never read again — so without a cap every interaction the server ever
     * handles stays on the heap until restart.
     */
    private static final int MAX_ENTRIES = 5000;

    // Cache entry with timestamp
    private record CacheEntry(ChecklistContext context, Instant timestamp) {}

    // In-memory cache: interactionId -> CacheEntry
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Get cached checklist context for an interaction.
     *
     * @param interactionId The conversation interaction ID
     * @return Cached ChecklistContext or null if not found/expired
     */
    public ChecklistContext get(String interactionId) {
        CacheEntry entry = cache.get(interactionId);
        if (entry == null) {
            return null;
        }

        // Check if expired
        if (isExpired(entry)) {
            cache.remove(interactionId);
            log.debug("[ChecklistCache] Entry expired for interaction: {}", interactionId);
            return null;
        }

        log.debug("[ChecklistCache] Cache hit for interaction: {}, operation: {}",
                interactionId, entry.context().operationType());
        return entry.context();
    }

    /**
     * Store checklist context in cache.
     *
     * @param interactionId The conversation interaction ID
     * @param context       The checklist context to cache
     */
    public void put(String interactionId, ChecklistContext context) {
        if (interactionId == null || context == null) {
            return;
        }
        cache.put(interactionId, new CacheEntry(context, Instant.now()));
        enforceCeiling();
        log.debug("[ChecklistCache] Cached context for interaction: {}, operation: {}",
                interactionId, context.operationType());
    }

    /**
     * Check if checklist context exists in cache (not expired).
     *
     * @param interactionId The conversation interaction ID
     * @return true if cached context exists and is valid
     */
    public boolean exists(String interactionId) {
        return get(interactionId) != null;
    }

    /**
     * Remove cached data for an interaction.
     *
     * @param interactionId The conversation interaction ID
     */
    public void evict(String interactionId) {
        cache.remove(interactionId);
        log.debug("[ChecklistCache] Evicted cache for interaction: {}", interactionId);
    }

    /**
     * Clear all cached data.
     */
    public void clear() {
        cache.clear();
        log.info("[ChecklistCache] Cleared all cache entries");
    }

    /**
     * Get cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clean up expired entries (can be called periodically).
     */
    public void cleanupExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        int removed = before - cache.size();
        if (removed > 0) {
            log.info("[ChecklistCache] Cleaned up {} expired cache entries", removed);
        }
    }

    /**
     * Keep the map bounded. Sweeps expired entries first; if that is not enough
     * (a burst of live conversations), drops the oldest back to the ceiling.
     * Runs only on the write that crosses the limit, so the cost is amortised.
     */
    private void enforceCeiling() {
        if (cache.size() <= MAX_ENTRIES) {
            return;
        }
        cleanupExpired();
        int excess = cache.size() - MAX_ENTRIES;
        if (excess <= 0) {
            return;
        }
        cache.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().timestamp()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(cache::remove);
        log.warn("[ChecklistCache] Over capacity: evicted {} oldest entries (ceiling {})",
                excess, MAX_ENTRIES);
    }

    private boolean isExpired(CacheEntry entry) {
        return Instant.now().isAfter(entry.timestamp().plusSeconds(CACHE_TTL_SECONDS));
    }
}
