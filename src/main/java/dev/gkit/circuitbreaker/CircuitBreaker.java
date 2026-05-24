package dev.gkit.circuitbreaker;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Thread-safe circuit breaker protecting downstream dependencies from cascading failures.
 * Three states: CLOSED (normal), OPEN (fail-fast), HALF_OPEN (probe).
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** Thrown when execute() is called on an OPEN circuit. */
    public static class OpenException extends RuntimeException {
        public OpenException(String msg) { super(msg); }
    }

    private final int failureThreshold;
    private final int successThreshold;
    private final Duration openTimeout;
    private final BiConsumer<State, State> onStateChange;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failures = new AtomicInteger(0);
    private final AtomicInteger probes = new AtomicInteger(0);
    private volatile Instant openedAt;

    private CircuitBreaker(Builder b) {
        this.failureThreshold = b.failureThreshold;
        this.successThreshold = b.successThreshold;
        this.openTimeout = b.openTimeout;
        this.onStateChange = b.onStateChange;
    }

    /** Executes fn if the breaker permits; throws OpenException if OPEN. */
    public <T> T execute(Callable<T> fn) throws Exception {
        checkState();
        try {
            T result = fn.call();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    /** Executes a Runnable. Convenience wrapper for execute(). */
    public void run(RunnableWithException fn) throws Exception {
        execute(() -> { fn.run(); return null; });
    }

    @FunctionalInterface public interface RunnableWithException { void run() throws Exception; }

    /** Returns the current state of the breaker. */
    public State state() { maybeTransitionFromOpen(); return state; }

    /** Manually resets the breaker to CLOSED. */
    public synchronized void reset() {
        transition(State.CLOSED); failures.set(0); probes.set(0);
    }

    private synchronized void checkState() {
        maybeTransitionFromOpen();
        if (state == State.OPEN) {
            Duration remaining = openTimeout.minus(Duration.between(openedAt, Instant.now()));
            throw new OpenException("Circuit OPEN, retry after " + remaining.toSeconds() + "s");
        }
    }

    private synchronized void recordSuccess() {
        if (state == State.CLOSED) { failures.set(0); }
        else if (state == State.HALF_OPEN) {
            if (probes.incrementAndGet() >= successThreshold) { failures.set(0); probes.set(0); transition(State.CLOSED); }
        }
    }

    private synchronized void recordFailure() {
        if (state == State.CLOSED) {
            if (failures.incrementAndGet() >= failureThreshold) { openedAt = Instant.now(); transition(State.OPEN); }
        } else if (state == State.HALF_OPEN) {
            probes.set(0); failures.set(0); openedAt = Instant.now(); transition(State.OPEN);
        }
    }

    private void maybeTransitionFromOpen() {
        if (state == State.OPEN && openedAt != null && Duration.between(openedAt, Instant.now()).compareTo(openTimeout) >= 0) {
            transition(State.HALF_OPEN);
        }
    }

    private void transition(State to) {
        if (state == to) return;
        State from = state; state = to;
        if (onStateChange != null) onStateChange.accept(from, to);
    }

    /** Builder for CircuitBreaker configuration. */
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private int failureThreshold = 5;
        private int successThreshold = 1;
        private Duration openTimeout = Duration.ofSeconds(60);
        private BiConsumer<State, State> onStateChange;
        public Builder failureThreshold(int n) { failureThreshold = n; return this; }
        public Builder successThreshold(int n) { successThreshold = n; return this; }
        public Builder openTimeout(Duration d) { openTimeout = d; return this; }
        public Builder onStateChange(BiConsumer<State,State> fn) { onStateChange = fn; return this; }
        public CircuitBreaker build() { return new CircuitBreaker(this); }
    }
}
