package dev.gkit.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token-bucket rate limiter with per-key variants.
 *
 * <pre>{@code
 * RateLimiter limiter = RateLimiter.create(100, 10); // 100 req/s, burst=10
 * if (!limiter.allow()) { throw new RateLimitExceededException(); }
 *
 * KeyedRateLimiter<String> keyed = KeyedRateLimiter.create(100, 10);
 * if (!keyed.allow(request.getRemoteAddr())) { ... }
 * }</pre>
 */
public final class RateLimiter {

    private final ReentrantLock lock = new ReentrantLock();
    private final double rate;
    private final double burst;
    private double tokens;
    private long lastFillNanos;

    private RateLimiter(double rate, int burst) {
        this.rate = rate;
        this.burst = burst;
        this.tokens = burst;
        this.lastFillNanos = System.nanoTime();
    }

    public static RateLimiter create(double ratePerSecond, int burst) {
        return new RateLimiter(ratePerSecond, burst);
    }

    public boolean allow() { return allowN(1); }

    public boolean allowN(int n) {
        lock.lock();
        try {
            refill();
            if (tokens < n) return false;
            tokens -= n;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Blocks until 1 token is available or the thread is interrupted. */
    public void acquire() throws InterruptedException { acquireN(1); }

    public void acquireN(int n) throws InterruptedException {
        while (true) {
            if (allowN(n)) return;
            long waitMs = (long) ((n - tokens) / rate * 1000) + 1;
            Thread.sleep(Math.max(waitMs, 1));
        }
    }

    public double getTokens() {
        lock.lock();
        try { refill(); return tokens; }
        finally { lock.unlock(); }
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsed = (now - lastFillNanos) / 1_000_000_000.0;
        tokens = Math.min(burst, tokens + elapsed * rate);
        lastFillNanos = now;
    }

    // -------------------------------------------------------------------------
    // Per-key limiter

    public static final class KeyedRateLimiter<K> {
        private final double rate;
        private final int burst;
        private final Duration ttl;
        private final Map<K, Entry> entries = new ConcurrentHashMap<>();

        private KeyedRateLimiter(double rate, int burst, Duration ttl) {
            this.rate = rate; this.burst = burst; this.ttl = ttl;
        }

        public static <K> KeyedRateLimiter<K> create(double ratePerSecond, int burst) {
            return new KeyedRateLimiter<>(ratePerSecond, burst, Duration.ofMinutes(10));
        }

        public boolean allow(K key) { return getLimiter(key).allow(); }
        public boolean allowN(K key, int n) { return getLimiter(key).allowN(n); }
        public void delete(K key) { entries.remove(key); }

        /** Evict limiters idle longer than TTL. */
        public int evict() {
            long cutoff = System.nanoTime() - ttl.toNanos();
            int n = 0;
            for (var it = entries.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (entry.getValue().lastSeen < cutoff) { it.remove(); n++; }
            }
            return n;
        }

        public int size() { return entries.size(); }

        private RateLimiter getLimiter(K key) {
            Entry e = entries.computeIfAbsent(key, k -> new Entry(new RateLimiter(rate, burst)));
            e.lastSeen = System.nanoTime();
            return e.limiter;
        }

        private static class Entry {
            final RateLimiter limiter;
            volatile long lastSeen;
            Entry(RateLimiter l) { this.limiter = l; this.lastSeen = System.nanoTime(); }
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException() { super("Rate limit exceeded"); }
    }
}
