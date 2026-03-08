package dev.gkit.health;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Composable health-check system with concurrent execution.
 *
 * <pre>{@code
 * Health.Group group = Health.Group.builder().checkTimeout(Duration.ofSeconds(5)).build();
 * group.register("database", ctx -> db.ping());
 * group.register("redis",    ctx -> redis.ping());
 * Health.Report report = group.check();
 * }</pre>
 */
public final class Health {

    private Health() {}

    @FunctionalInterface
    public interface Checker {
        void check() throws Exception;
    }

    public record Status(String name, boolean healthy, String error) {}

    public record Report(boolean healthy, List<Status> checks, String duration) {}

    public static final class Group {
        private final Duration checkTimeout;
        private final List<Registration> checkers = new CopyOnWriteArrayList<>();

        private Group(Builder b) { this.checkTimeout = b.checkTimeout; }

        private record Registration(String name, Checker checker) {}

        public void register(String name, Checker checker) {
            checkers.add(new Registration(name, checker));
        }

        public Report check() {
            Instant start = Instant.now();
            List<Registration> snapshot = new ArrayList<>(checkers);

            ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
            List<Future<Status>> futures = new ArrayList<>();

            for (Registration r : snapshot) {
                futures.add(exec.submit(() -> {
                    try {
                        r.checker().check();
                        return new Status(r.name(), true, null);
                    } catch (Exception e) {
                        return new Status(r.name(), false, e.getMessage());
                    }
                }));
            }
            exec.shutdown();

            List<Status> statuses = new ArrayList<>();
            boolean overall = true;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Status s = futures.get(i).get(checkTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    statuses.add(s);
                    if (!s.healthy()) overall = false;
                } catch (TimeoutException e) {
                    statuses.add(new Status(snapshot.get(i).name(), false, "timeout"));
                    overall = false;
                } catch (Exception e) {
                    statuses.add(new Status(snapshot.get(i).name(), false, e.getMessage()));
                    overall = false;
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            return new Report(overall, statuses, elapsed.toMillis() + "ms");
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private Duration checkTimeout = Duration.ofSeconds(5);

            public Builder checkTimeout(Duration d) { this.checkTimeout = d; return this; }
            public Group build() { return new Group(this); }
        }
    }
}
