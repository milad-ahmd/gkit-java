package dev.gkit.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Strategy for computing wait time between retry attempts.
 *
 * <p>Implementations should be stateless and thread-safe.
 * Use the static factory methods to create common strategies.
 *
 * <pre>{@code
 * Backoff b = Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(10))
 *                    .withJitter();
 * }</pre>
 */
@FunctionalInterface
public interface Backoff {

    /**
     * Returns the duration to wait before the next attempt.
     *
     * @param attempt zero-based attempt index (0 = first retry)
     * @return the delay duration
     */
    Duration next(int attempt);

    /**
     * Creates a constant backoff that always waits the same duration.
     *
     * @param d the constant wait duration
     * @return a constant {@link Backoff}
     */
    static Backoff constant(Duration d) { return _ -> d; }

    /**
     * Creates an exponential backoff with a configurable multiplier and cap.
     *
     * @param initial    the initial delay for attempt 0
     * @param multiplier growth factor per attempt (e.g. 2.0 = doubles each time)
     * @param max        the maximum delay
     * @return an exponential {@link Backoff}
     */
    static Backoff exponential(Duration initial, double multiplier, Duration max) {
        return attempt -> {
            double ms = initial.toMillis() * Math.pow(multiplier, attempt);
            return Duration.ofMillis(Math.min((long) ms, max.toMillis()));
        };
    }

    /**
     * Decorates this backoff with full jitter: the actual delay is a random
     * value in {@code [0, base]}, which avoids thundering-herd problems.
     *
     * @return a jitter-decorated {@link Backoff}
     */
    default Backoff withJitter() {
        return attempt -> {
            Duration base = this.next(attempt);
            long jitterMs = (long) (ThreadLocalRandom.current().nextDouble() * base.toMillis());
            return Duration.ofMillis(jitterMs);
        };
    }
}
