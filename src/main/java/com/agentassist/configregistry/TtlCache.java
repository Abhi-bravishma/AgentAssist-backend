package com.agentassist.configregistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Minimal TTL cache (ConcurrentHashMap + timestamp) per the Part 1 spec —
 * Caffeine is not on the classpath and one more dependency is not worth it
 * for a 60-second template cache. Loader failures propagate and are never
 * cached; {@link #clear()} backs the portal's evict-all.
 */
final class TtlCache<V> {

    private record Entry<V>(V value, long expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry<V>> map = new ConcurrentHashMap<>();
    private final long ttlMillis;

    TtlCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    V get(String key, Function<String, V> loader) {
        Entry<V> cached = map.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt()) {
            return cached.value();
        }
        V value = loader.apply(key);
        map.put(key, new Entry<>(value, now + ttlMillis));
        return value;
    }

    void clear() {
        map.clear();
    }
}
