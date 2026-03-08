package dev.gkit.graceful;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates ordered, timeout-aware graceful shutdown of multiple components.
 *
 * <pre>{@code
 * GracefulShutdown g = GracefulShutdown.builder().timeout(Duration.ofSeconds(30)).build();
 * g.register("http-server", () -> server.stop());
 * g.register("worker-pool", () -> pool.drain());
 * Runtime.getRuntime().addShutdownHook(new Thread(() -> g.shutdown()));
 * }</pre>
 */
public final class GracefulShutdown {

    private final Duration timeout;
    private final List<Registration> hooks = new ArrayList<>();
    private final Object lock = new Object();

    private GracefulShutdown(Builder b) {
        this.timeout = b.timeout;
    }

    @FunctionalInterface
    public interface ShutdownHook {
        void shutdown() throws Exception;
    }

    private record Registration(String name, ShutdownHook hook) {}

    public void register(String name, ShutdownHook hook) {
        synchronized (lock) {
            hooks.add(new Registration(name, hook));
        }
    }

    /**
     * Runs all hooks in LIFO order within the configured timeout.
     * Collects errors but does not short-circuit.
     */
    public void shutdown() {
        List<Registration> copy;
        synchronized (lock) {
            copy = new ArrayList<>(hooks);
        }
        Collections.reverse(copy);

        List<String> errors = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        for (Registration r : copy) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                errors.add(r.name() + ": timed out");
                continue;
            }
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<?> future = exec.submit(() -> {
                try { r.hook().shutdown(); }
                catch (Exception e) { throw new RuntimeException(r.name() + ": " + e.getMessage(), e); }
            });
            try {
                future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                errors.add(r.name() + ": timed out");
            } catch (Exception e) {
                errors.add(r.name() + ": " + e.getMessage());
            } finally {
                exec.shutdownNow();
            }
        }

        if (!errors.isEmpty()) {
            throw new ShutdownException("Shutdown errors: " + String.join("; ", errors));
        }
    }

    /** Installs SIGTERM/SIGINT JVM shutdown hook automatically. */
    public GracefulShutdown installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "gkit-graceful-shutdown"));
        return this;
    }

    public static class ShutdownException extends RuntimeException {
        public ShutdownException(String msg) { super(msg); }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Duration timeout = Duration.ofSeconds(30);

        public Builder timeout(Duration d) { this.timeout = d; return this; }
        public GracefulShutdown build() { return new GracefulShutdown(this); }
    }
}
