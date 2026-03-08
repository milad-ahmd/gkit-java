package dev.gkit.retry;

import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * Configuration options for {@link Retry}.
 *
 * <p>Use the builder to configure options fluently:
 * <pre>{@code
 * RetryOptions opts = RetryOptions.builder()
 *     .maxAttempts(5)
 *     .backoff(Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(10)).withJitter())
 *     .onRetry((attempt, err) -> log.warn("Retry #{}: {}", attempt, err.getMessage()))
 *     .build();
 * }</pre>
 */
public record RetryOptions(
    int maxAttempts,
    Backoff backoff,
    BiConsumer<Integer, Throwable> onRetry
) {
    /**
     * Returns a new {@link Builder} with sensible defaults:
     * 3 attempts, exponential backoff with jitter, no-op on-retry callback.
     */
    public static Builder builder() { return new Builder(); }

    /** Fluent builder for {@link RetryOptions}. */
    public static class Builder {
        private int maxAttempts = 3;
        private Backoff backoff = Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(10)).withJitter();
        private BiConsumer<Integer, Throwable> onRetry = (a, e) -> {};

        /** Sets the maximum number of total attempts (including the first). */
        public Builder maxAttempts(int n) { this.maxAttempts = n; return this; }

        /** Sets the backoff strategy between attempts. */
        public Builder backoff(Backoff b) { this.backoff = b; return this; }

        /** Sets a callback invoked after each failed attempt. */
        public Builder onRetry(BiConsumer<Integer, Throwable> fn) { this.onRetry = fn; return this; }

        /** Builds and returns the {@link RetryOptions}. */
        public RetryOptions build() { return new RetryOptions(maxAttempts, backoff, onRetry); }
    }
}
