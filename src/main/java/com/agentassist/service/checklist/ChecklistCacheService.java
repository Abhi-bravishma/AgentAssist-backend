package com.agentassist.service.checklist;

import com.agentassist.service.checklist.ChecklistService.ChecklistContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    private boolean isExpired(CacheEntry entry) {
        return Instant.now().isAfter(entry.timestamp().plusSeconds(CACHE_TTL_SECONDS));
    }
}
