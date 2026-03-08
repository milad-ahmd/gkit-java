package dev.gkit.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Redis-backed distributed lock with automatic lease renewal.
 *
 * <pre>{@code
 * DistributedLock locker = new DistributedLock(redisTemplate);
 * locker.withLock("billing:invoice:123", Duration.ofSeconds(30), () -> {
 *     processInvoice();
 * });
 * }</pre>
 */
public final class DistributedLock {

    private static final String RELEASE_SCRIPT =
        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('DEL', KEYS[1]) " +
        "else return 0 end";

    private static final String RENEW_SCRIPT =
        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " +
        "else return 0 end";

    private final StringRedisTemplate redis;
    private final int retryCount;
    private final Duration retryInterval;

    public DistributedLock(StringRedisTemplate redis) {
        this(redis, 0, Duration.ofMillis(100));
    }

    public DistributedLock(StringRedisTemplate redis, int retryCount, Duration retryInterval) {
        this.redis = redis;
        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
    }

    public static class LockNotAcquiredException extends RuntimeException {
        public LockNotAcquiredException(String key) { super("Lock not acquired: " + key); }
    }

    /** Tries to acquire the lock once. Returns the token or null. */
    public String tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /** Acquires the lock, retrying up to retryCount times. */
    public String acquire(String key, Duration ttl) throws InterruptedException {
        int attempts = 0;
        while (true) {
            String token = tryAcquire(key, ttl);
            if (token != null) return token;
            if (retryCount > 0 && attempts++ >= retryCount) {
                throw new LockNotAcquiredException(key);
            }
            Thread.sleep(retryInterval.toMillis());
        }
    }

    /** Releases the lock identified by key+token. */
    public boolean release(String key, String token) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
        Long result = redis.execute(script, List.of(key), token);
        return Long.valueOf(1L).equals(result);
    }

    /** Acquires, runs fn, releases. Throws LockNotAcquiredException if not acquired. */
    public <T> T withLock(String key, Duration ttl, Callable<T> fn) throws Exception {
        String token = acquire(key, ttl);
        try {
            return fn.call();
        } finally {
            release(key, token);
        }
    }

    public void withLock(String key, Duration ttl, Runnable fn) throws InterruptedException {
        String token = acquire(key, ttl);
        try {
            fn.run();
        } finally {
            release(key, token);
        }
    }
}
