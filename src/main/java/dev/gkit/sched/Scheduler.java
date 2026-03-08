package dev.gkit.sched;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Lightweight job scheduler backed by a bounded thread pool.
 *
 * <pre>{@code
 * Scheduler sched = Scheduler.builder()
 *     .workers(4)
 *     .onError((job, err) -> log.error("Job {} failed: {}", job.name(), err.getMessage()))
 *     .build();
 *
 * sched.every(Duration.ofMinutes(1), "cleanup", ctx -> db.deleteOldRecords());
 * sched.after(Duration.ofSeconds(5), "warmup", ctx -> cache.warm());
 * sched.start();
 * }</pre>
 */
public final class Scheduler {

    public record Job(String name, Runnable fn) {}

    @FunctionalInterface
    public interface ErrorHandler { void handle(Job job, Exception error); }

    private final int workers;
    private final ErrorHandler onError;
    private final List<ScheduleEntry> schedules = new ArrayList<>();
    private ScheduledExecutorService executor;

    private Scheduler(Builder b) {
        this.workers = b.workers;
        this.onError = b.onError;
    }

    private record ScheduleEntry(Job job, Duration interval, Duration delay, boolean once) {}

    public Scheduler every(Duration interval, String name, Runnable fn) {
        schedules.add(new ScheduleEntry(new Job(name, fn), interval, Duration.ZERO, false));
        return this;
    }

    public Scheduler after(Duration delay, String name, Runnable fn) {
        schedules.add(new ScheduleEntry(new Job(name, fn), null, delay, true));
        return this;
    }

    public void start() {
        executor = Executors.newScheduledThreadPool(workers);
        for (ScheduleEntry entry : schedules) {
            if (entry.once()) {
                executor.schedule(() -> dispatch(entry.job()), entry.delay().toMillis(), TimeUnit.MILLISECONDS);
            } else {
                executor.scheduleAtFixedRate(() -> dispatch(entry.job()),
                    0, entry.interval().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public void stop() {
        if (executor != null) {
            executor.shutdown();
            try { executor.awaitTermination(30, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private void dispatch(Job job) {
        try { job.fn().run(); }
        catch (Exception e) { if (onError != null) onError.handle(job, e); }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int workers = Runtime.getRuntime().availableProcessors();
        private ErrorHandler onError;

        public Builder workers(int n) { this.workers = n; return this; }
        public Builder onError(ErrorHandler h) { this.onError = h; return this; }
        public Scheduler build() { return new Scheduler(this); }
    }
}
