package dev.gkit.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryTest {

    private static RetryOptions noDelayOpts(int maxAttempts) {
        return RetryOptions.builder()
                .maxAttempts(maxAttempts)
                .backoff(Backoff.constant(java.time.Duration.ZERO))
                .build();
    }

    @Test
    @DisplayName("succeeds on first attempt and returns result")
    void succeedsOnFirstAttempt() throws Exception {
        String result = Retry.execute(() -> "hello", noDelayOpts(3));
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("retries on failure and eventually succeeds")
    void retriesAndEventuallySucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        String result = Retry.execute(() -> {
            if (attempts.incrementAndGet() < 3) throw new RuntimeException("fail");
            return "ok";
        }, noDelayOpts(5));
        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    @DisplayName("throws MaxAttemptsExceededException when all attempts fail")
    void throwsMaxAttemptsExceeded() {
        AtomicInteger attempts = new AtomicInteger(0);
        MaxAttemptsExceededException ex = assertThrows(MaxAttemptsExceededException.class, () ->
                Retry.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fails");
                }, noDelayOpts(3))
        );
        assertEquals(3, attempts.get());
        assertEquals(3, ex.getMaxAttempts());
        assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("stops immediately on StopException without consuming all attempts")
    void stopsImmediatelyOnStopException() {
        AtomicInteger attempts = new AtomicInteger(0);
        assertThrows(RuntimeException.class, () ->
                Retry.execute(() -> {
                    attempts.incrementAndGet();
                    throw Retry.stop(new IllegalArgumentException("abort"));
                }, noDelayOpts(10))
        );
        assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("StopException re-throws the wrapped cause")
    void stopExceptionRethrowsCause() {
        IllegalArgumentException cause = new IllegalArgumentException("bad input");
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                Retry.execute(() -> { throw Retry.stop(cause); }, noDelayOpts(5))
        );
        assertSame(cause, ex);
    }

    @Test
    @DisplayName("onRetry callback is invoked on each failure")
    void onRetryCallbackInvokedOnEachFailure() throws Exception {
        List<Integer> attemptNumbers = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger(0);

        RetryOptions opts = RetryOptions.builder()
                .maxAttempts(4)
                .backoff(Backoff.constant(java.time.Duration.ZERO))
                .onRetry((attempt, err) -> {
                    attemptNumbers.add(attempt);
                    errors.add(err);
                })
                .build();

        Retry.execute(() -> {
            if (calls.incrementAndGet() < 4) throw new RuntimeException("fail");
            return "done";
        }, opts);

        assertEquals(List.of(1, 2, 3), attemptNumbers);
        assertEquals(3, errors.size());
    }

    @Test
    @DisplayName("executeVoid succeeds without throwing")
    void executeVoidSucceeds() {
        AtomicInteger ran = new AtomicInteger(0);
        assertDoesNotThrow(() -> Retry.executeVoid(ran::incrementAndGet, noDelayOpts(3)));
        assertEquals(1, ran.get());
    }

    @Test
    @DisplayName("executeVoid throws MaxAttemptsExceededException after all failures")
    void executeVoidThrowsAfterAllFailures() {
        assertThrows(MaxAttemptsExceededException.class, () ->
                Retry.executeVoid(() -> { throw new RuntimeException("fail"); }, noDelayOpts(2))
        );
    }

    @Test
    @DisplayName("maxAttempts of 1 means no retries at all")
    void maxAttemptsOfOneNoRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        assertThrows(MaxAttemptsExceededException.class, () ->
                Retry.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                }, noDelayOpts(1))
        );
        assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("Backoff.constant always returns the same duration")
    void backoffConstant() {
        java.time.Duration d = java.time.Duration.ofMillis(250);
        Backoff b = Backoff.constant(d);
        for (int i = 0; i < 5; i++) {
            assertEquals(d, b.next(i));
        }
    }

    @Test
    @DisplayName("Backoff.exponential grows with multiplier and caps at max")
    void backoffExponentialGrowsAndCaps() {
        java.time.Duration initial = java.time.Duration.ofMillis(100);
        java.time.Duration max = java.time.Duration.ofSeconds(1);
        Backoff b = Backoff.exponential(initial, 2.0, max);

        assertEquals(100, b.next(0).toMillis());
        assertEquals(200, b.next(1).toMillis());
        assertEquals(400, b.next(2).toMillis());
        // Should be capped at max
        assertEquals(1000, b.next(10).toMillis());
    }

    @Test
    @DisplayName("Backoff.withJitter returns non-negative duration within base range")
    void backoffWithJitterIsNonNegative() {
        Backoff base = Backoff.constant(java.time.Duration.ofMillis(500));
        Backoff jittered = base.withJitter();
        for (int i = 0; i < 20; i++) {
            java.time.Duration d = jittered.next(i);
            assertTrue(d.toMillis() >= 0);
            assertTrue(d.toMillis() <= 500);
        }
    }

    @Test
    @DisplayName("Retry.stop factory method creates StopException")
    void stopFactoryCreatesStopException() {
        RuntimeException cause = new RuntimeException("root");
        StopException stop = Retry.stop(cause);
        assertNotNull(stop);
        assertSame(cause, stop.getCause());
    }
}
