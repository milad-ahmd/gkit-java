package dev.gkit.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe generic LRU cache with optional per-entry TTL.
 *
 * <p>Backed by {@link LinkedHashMap} in access-order mode with an eldest-entry
 * removal hook for size eviction. TTL eviction is lazy: expired entries are
 * removed on the first access after expiry.
 *
 * <pre>{@code
 * LruCache<String, Product> cache = LruCache.<String, Product>builder()
 *     .maxSize(10_000)
 *     .ttl(Duration.ofMinutes(5))
 *     .build();
 *
 * cache.put("p1", product);
 * Optional<Product> p = cache.get("p1");
 * }</pre>
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LruCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
        boolean expired() { return expiresAt != null && Instant.now().isAfter(expiresAt); }
    }

    private final int maxSize;
    private final Duration ttl;
    private final LinkedHashMap<K, Entry<V>> map;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();

    private LruCache(Builder<K, V> b) {
        this.maxSize = b.maxSize;
        this.ttl = b.ttl;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                if (size() > maxSize) { evictions.increment(); return true; }
                return false;
            }
        };
    }

    /**
     * Retrieves a value by key.
     *
     * @param key the lookup key
     * @return an {@link Optional} containing the value, or empty if missing or expired
     */
    public Optional<V> get(K key) {
        lock.writeLock().lock(); // write lock because LinkedHashMap.get mutates access order
        try {
            Entry<V> e = map.get(key);
            if (e == null || e.expired()) {
                if (e != null) { map.remove(key); evictions.increment(); }
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(e.value());
        } finally { lock.writeLock().unlock(); }
    }

    /**
     * Inserts or replaces a value.
     *
     * @param key   the key
     * @param value the value to store
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            Instant exp = ttl != null ? Instant.now().plus(ttl) : null;
            map.put(key, new Entry<>(value, exp));
        } finally { lock.writeLock().unlock(); }
    }

    /**
     * Removes the entry for the given key.
     *
     * @param key the key to remove
     */
    public void remove(K key) {
        lock.writeLock().lock();
        try { map.remove(key); }
        finally { lock.writeLock().unlock(); }
    }

    /** Removes all entries. */
    public void clear() {
        lock.writeLock().lock();
        try { map.clear(); }
        finally { lock.writeLock().unlock(); }
    }

    /**
     * Returns the current number of entries (including potentially expired ones
     * not yet evicted by lazy expiration).
     */
    public int size() {
        lock.readLock().lock();
        try { return map.size(); }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Returns a snapshot of cache statistics.
     *
     * @return current {@link CacheStats}
     */
    public CacheStats stats() {
        return new CacheStats(hits.sum(), misses.sum(), evictions.sum(), size());
    }

    /**
     * Cache performance counters snapshot.
     *
     * @param hits      total successful lookups
     * @param misses    total failed lookups
     * @param evictions total entries evicted (size or TTL)
     * @param size      current entry count
     */
    public record CacheStats(long hits, long misses, long evictions, int size) {
        /** Returns the fraction of accesses that were cache hits, or 0 if no accesses yet. */
        public double hitRate() { long t = hits + misses; return t == 0 ? 0 : (double) hits / t; }
    }

    /**
     * Creates a new builder.
     *
     * @param <K> key type
     * @param <V> value type
     */
    public static <K, V> Builder<K, V> builder() { return new Builder<>(); }

    /** Fluent builder for {@link LruCache}. */
    public static class Builder<K, V> {
        private int maxSize = 1000;
        private Duration ttl;

        /** Sets the maximum number of entries before LRU eviction kicks in. */
        public Builder<K, V> maxSize(int n) { this.maxSize = n; return this; }

        /** Sets the per-entry TTL. {@code null} means no expiration. */
        public Builder<K, V> ttl(Duration d) { this.ttl = d; return this; }

        /** Builds and returns the {@link LruCache}. */
        public LruCache<K, V> build() { return new LruCache<>(this); }
    }
}
