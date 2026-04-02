package com.agentassist.service.salesforce;

import com.agentassist.dto.salesforce.CustomerPolicyData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache service for storing customer policy data per interaction.
 * Caches Salesforce data to avoid repeated API calls within the same conversation.
 */
@Slf4j
@Service
public class PolicyCacheService {

    // Cache TTL in seconds (30 minutes)
    private static final long CACHE_TTL_SECONDS = 1800;

    // Cache entry with timestamp
    private record CacheEntry(CustomerPolicyData data, Instant timestamp) {}

    // In-memory cache: interactionId -> CacheEntry
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Get cached policy data for an interaction.
     *
     * @param interactionId The conversation interaction ID
     * @return Cached CustomerPolicyData or null if not found/expired
     */
    public CustomerPolicyData get(String interactionId) {
        CacheEntry entry = cache.get(interactionId);
        if (entry == null) {
            return null;
        }

        // Check if expired
        if (isExpired(entry)) {
            cache.remove(interactionId);
            log.debug("Cache entry expired for interaction: {}", interactionId);
            return null;
        }

        log.debug("Cache hit for interaction: {}", interactionId);
        return entry.data();
    }

    /**
     * Store policy data in cache.
     *
     * @param interactionId The conversation interaction ID
     * @param data          The customer policy data to cache
     */
    public void put(String interactionId, CustomerPolicyData data) {
        if (interactionId == null || data == null) {
            return;
        }
        cache.put(interactionId, new CacheEntry(data, Instant.now()));
        log.debug("Cached policy data for interaction: {}", interactionId);
    }

    /**
     * Check if policy data exists in cache (not expired).
     *
     * @param interactionId The conversation interaction ID
     * @return true if cached data exists and is valid
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
        log.debug("Evicted cache for interaction: {}", interactionId);
    }

    /**
     * Clear all cached data.
     */
    public void clear() {
        cache.clear();
        log.info("Cleared all policy cache entries");
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
            log.info("Cleaned up {} expired cache entries", removed);
        }
    }

    private boolean isExpired(CacheEntry entry) {
        return Instant.now().isAfter(entry.timestamp().plusSeconds(CACHE_TTL_SECONDS));
    }
}
