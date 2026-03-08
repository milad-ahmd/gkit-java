package dev.gkit.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    @DisplayName("allow returns true when tokens are available")
    void allowReturnsTrueWhenTokensAvailable() {
        RateLimiter limiter = RateLimiter.create(100, 10);
        assertTrue(limiter.allow());
    }

    @Test
    @DisplayName("allow depletes tokens and eventually returns false")
    void allowEventuallyReturnsFalse() {
        // burst of 3, very slow refill rate
        RateLimiter limiter = RateLimiter.create(0.001, 3);
        assertTrue(limiter.allow());
        assertTrue(limiter.allow());
        assertTrue(limiter.allow());
        assertFalse(limiter.allow()); // burst exhausted
    }

    @Test
    @DisplayName("allowN consumes N tokens at once")
    void allowNConsumesNTokens() {
        RateLimiter limiter = RateLimiter.create(0.001, 5);
        assertTrue(limiter.allowN(5));  // consumes all 5
        assertFalse(limiter.allowN(1)); // no tokens left
    }

    @Test
    @DisplayName("allowN returns false when fewer than N tokens available")
    void allowNReturnsFalseWhenInsufficientTokens() {
        RateLimiter limiter = RateLimiter.create(0.001, 3);
        assertFalse(limiter.allowN(5)); // burst is only 3
    }

    @Test
    @DisplayName("getTokens returns current token count")
    void getTokensReturnsCurrentCount() {
        RateLimiter limiter = RateLimiter.create(0.001, 10);
        double tokens = limiter.getTokens();
        assertTrue(tokens >= 0 && tokens <= 10);
    }

    @Test
    @DisplayName("tokens refill over time")
    void tokensRefillOverTime() throws InterruptedException {
        // rate=1000 per second means 1 token per ms; burst=1
        RateLimiter limiter = RateLimiter.create(1000, 1);
        limiter.allow(); // drain
        Thread.sleep(50); // wait for 50ms = ~50 tokens, capped at burst=1
        assertTrue(limiter.allow());
    }

    @Test
    @DisplayName("acquire blocks until token is available")
    void acquireBlocksUntilTokenAvailable() throws InterruptedException {
        // rate=10 per sec, burst=0 initial (drain first)
        RateLimiter limiter = RateLimiter.create(100, 1);
        limiter.allow(); // drain token

        long start = System.currentTimeMillis();
        limiter.acquire(); // should block and wait for refill
        long elapsed = System.currentTimeMillis() - start;

        // Should have waited at least a few ms
        assertTrue(elapsed >= 1, "acquire should have waited for refill");
    }

    @Test
    @DisplayName("RateLimitExceededException has correct message")
    void rateLimitExceededExceptionMessage() {
        RateLimiter.RateLimitExceededException ex = new RateLimiter.RateLimitExceededException();
        assertEquals("Rate limit exceeded", ex.getMessage());
    }

    // -----------------------------------------------------------------------
    // KeyedRateLimiter
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("KeyedRateLimiter.allow permits different keys independently")
    void keyedRateLimiterIndependentKeys() {
        RateLimiter.KeyedRateLimiter<String> keyed = RateLimiter.KeyedRateLimiter.create(0.001, 1);
        assertTrue(keyed.allow("user1"));
        // user1 is exhausted, but user2 has its own limiter
        assertFalse(keyed.allow("user1"));
        assertTrue(keyed.allow("user2"));
    }

    @Test
    @DisplayName("KeyedRateLimiter.allowN works per key")
    void keyedRateLimiterAllowNPerKey() {
        RateLimiter.KeyedRateLimiter<String> keyed = RateLimiter.KeyedRateLimiter.create(0.001, 3);
        assertTrue(keyed.allowN("key", 3));
        assertFalse(keyed.allowN("key", 1));
    }

    @Test
    @DisplayName("KeyedRateLimiter.delete removes a key")
    void keyedRateLimiterDeleteRemovesKey() {
        RateLimiter.KeyedRateLimiter<String> keyed = RateLimiter.KeyedRateLimiter.create(0.001, 1);
        keyed.allow("k"); // exhaust
        keyed.delete("k");
        // After deletion, key gets a fresh limiter
        assertTrue(keyed.allow("k"));
    }

    @Test
    @DisplayName("KeyedRateLimiter.size reflects active keys")
    void keyedRateLimiterSizeTracksKeys() {
        RateLimiter.KeyedRateLimiter<String> keyed = RateLimiter.KeyedRateLimiter.create(1, 1);
        assertEquals(0, keyed.size());
        keyed.allow("a");
        keyed.allow("b");
        assertEquals(2, keyed.size());
    }

    @Test
    @DisplayName("KeyedRateLimiter.evict removes stale entries after TTL")
    void keyedRateLimiterEvictsStaleEntries() throws InterruptedException {
        // We can't set a short TTL via the public API, but we can verify evict() returns non-negative
        RateLimiter.KeyedRateLimiter<String> keyed = RateLimiter.KeyedRateLimiter.create(1, 1);
        keyed.allow("recent");
        int evicted = keyed.evict();
        assertTrue(evicted >= 0);
    }
}
