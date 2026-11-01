package dev.yggskins.skin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en memoria con expiracion por entrada.
 */
public final class SkinCache<K, V> {

    private record Entry<V>(V value, long expiresAt) {
    }

    private final long ttlMillis;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public SkinCache(long ttlSeconds) {
        this.ttlMillis = Math.max(0L, ttlSeconds) * 1000L;
    }

    public V get(K key) {
        if (key == null) {
            return null;
        }
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (ttlMillis <= 0 || entry.expiresAt() < System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }
        entries.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    public void invalidate(K key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    public void clear() {
        entries.clear();
    }
}
