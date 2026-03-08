package dev.gkit.rediscache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Redis-backed generic cache with JSON serialization.
 *
 * <pre>{@code
 * RedisCache<Product> cache = new RedisCache<>(redisTemplate, "products:", Product.class);
 *
 * Product p = cache.getOrSet("prod-123", Duration.ofMinutes(10), () -> db.findProduct("prod-123"));
 * cache.set("prod-456", product, Duration.ofHours(1));
 * }</pre>
 */
public final class RedisCache<V> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final String prefix;
    private final Class<V> valueType;

    public RedisCache(StringRedisTemplate redis, String prefix, Class<V> valueType) {
        this.redis = redis;
        this.prefix = prefix;
        this.valueType = valueType;
    }

    public Optional<V> get(String key) {
        String raw = redis.opsForValue().get(prefix + key);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(MAPPER.readValue(raw, valueType));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void set(String key, V value, Duration ttl) {
        try {
            String raw = MAPPER.writeValueAsString(value);
            if (ttl.isZero()) redis.opsForValue().set(prefix + key, raw);
            else redis.opsForValue().set(prefix + key, raw, ttl);
        } catch (Exception e) {
            throw new CacheException("Failed to serialize value for key: " + key, e);
        }
    }

    public void delete(String key) {
        redis.delete(prefix + key);
    }

    /** Get cached value or compute, cache, and return it. */
    public V getOrSet(String key, Duration ttl, Callable<V> loader) throws Exception {
        Optional<V> cached = get(key);
        if (cached.isPresent()) return cached.get();
        V value = loader.call();
        set(key, value, ttl);
        return value;
    }

    /** Flush all keys under this cache's prefix. */
    public void flush() {
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    /** Ping Redis — use as a health check. */
    public boolean ping() {
        try {
            return "PONG".equals(redis.getConnectionFactory()
                .getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }

    public static class CacheException extends RuntimeException {
        public CacheException(String msg, Throwable cause) { super(msg, cause); }
    }
}
