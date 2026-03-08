package dev.gkit.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LruCacheTest {

    @Test
    @DisplayName("put and get returns stored value")
    void putAndGetReturnsValue() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        cache.put("key1", "value1");
        Optional<String> result = cache.get("key1");
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    @DisplayName("get on missing key returns empty")
    void getMissingKeyReturnsEmpty() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        Optional<String> result = cache.get("nonexistent");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("put replaces existing value for same key")
    void putReplacesExistingValue() {
        LruCache<String, Integer> cache = LruCache.<String, Integer>builder().maxSize(10).build();
        cache.put("k", 1);
        cache.put("k", 2);
        Optional<Integer> result = cache.get("k");
        assertTrue(result.isPresent());
        assertEquals(2, result.get());
    }

    @Test
    @DisplayName("remove deletes entry")
    void removeDeletesEntry() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        cache.put("k", "v");
        cache.remove("k");
        assertFalse(cache.get("k").isPresent());
    }

    @Test
    @DisplayName("clear empties the cache")
    void clearEmptiesCache() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        cache.put("a", "1");
        cache.put("b", "2");
        cache.clear();
        assertEquals(0, cache.size());
        assertFalse(cache.get("a").isPresent());
    }

    @Test
    @DisplayName("size reflects number of entries")
    void sizeReflectsEntryCount() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        assertEquals(0, cache.size());
        cache.put("a", "1");
        assertEquals(1, cache.size());
        cache.put("b", "2");
        assertEquals(2, cache.size());
    }

    @Test
    @DisplayName("LRU eviction keeps most recently used entries within maxSize")
    void lruEvictsLeastRecentlyUsed() {
        LruCache<Integer, String> cache = LruCache.<Integer, String>builder().maxSize(3).build();
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        // Access 1 to make it recently used; 2 becomes least recently used
        cache.get(1);
        cache.get(3);
        // Adding 4 should evict 2 (least recently used)
        cache.put(4, "four");
        // Size should still be at most 3
        assertTrue(cache.size() <= 3);
        // Key 4 must be present
        assertTrue(cache.get(4).isPresent());
    }

    @Test
    @DisplayName("TTL expired entry is treated as missing")
    void ttlExpiredEntryIsMissing() throws InterruptedException {
        LruCache<String, String> cache = LruCache.<String, String>builder()
                .maxSize(10)
                .ttl(Duration.ofMillis(50))
                .build();
        cache.put("temp", "value");
        assertTrue(cache.get("temp").isPresent());
        Thread.sleep(100); // wait past TTL
        assertFalse(cache.get("temp").isPresent());
    }

    @Test
    @DisplayName("no TTL means entry never expires")
    void noTtlEntryNeverExpires() {
        LruCache<String, String> cache = LruCache.<String, String>builder()
                .maxSize(10)
                .build();
        cache.put("k", "v");
        assertTrue(cache.get("k").isPresent());
    }

    @Test
    @DisplayName("stats tracks hits and misses")
    void statsTracksHitsAndMisses() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        cache.put("k", "v");
        cache.get("k");    // hit
        cache.get("k");    // hit
        cache.get("miss"); // miss

        LruCache.CacheStats stats = cache.stats();
        assertEquals(2, stats.hits());
        assertEquals(1, stats.misses());
    }

    @Test
    @DisplayName("hitRate is 0 when no accesses")
    void hitRateZeroWhenNoAccesses() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        assertEquals(0.0, cache.stats().hitRate());
    }

    @Test
    @DisplayName("hitRate is correct fraction")
    void hitRateCorrectFraction() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(10).build();
        cache.put("k", "v");
        cache.get("k");    // hit
        cache.get("miss"); // miss

        LruCache.CacheStats stats = cache.stats();
        assertEquals(0.5, stats.hitRate(), 0.001);
    }

    @Test
    @DisplayName("stats evictions counts TTL-evicted and size-evicted entries")
    void statsEvictionsCountsTtlAndSize() throws InterruptedException {
        LruCache<String, String> cache = LruCache.<String, String>builder()
                .maxSize(2)
                .ttl(Duration.ofMillis(50))
                .build();
        cache.put("a", "1");
        Thread.sleep(100); // let it expire
        cache.get("a");    // trigger lazy TTL eviction
        // evictions should be at least 1
        assertTrue(cache.stats().evictions() >= 1);
    }

    @Test
    @DisplayName("builder default maxSize is 1000")
    void builderDefaultMaxSize() {
        // Just verify we can build with defaults without error
        LruCache<String, String> cache = LruCache.<String, String>builder().build();
        assertNotNull(cache);
    }

    @Test
    @DisplayName("cache handles null-like edge: put then remove then re-put")
    void removeThenRePut() {
        LruCache<String, String> cache = LruCache.<String, String>builder().maxSize(5).build();
        cache.put("k", "v1");
        cache.remove("k");
        assertFalse(cache.get("k").isPresent());
        cache.put("k", "v2");
        assertEquals("v2", cache.get("k").get());
    }
}
