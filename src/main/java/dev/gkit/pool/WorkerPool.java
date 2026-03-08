package dev.gkit.pool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

/**
 * Bounded worker pool backed by Java 21 virtual threads.
 *
 * <p>Mirrors the Go generic pool with {@code Submit}/{@code TrySubmit}/{@code Stop}/{@code Stats}.
 * Virtual threads provide the same lightweight concurrency model as Go goroutines —
 * starting thousands of workers incurs negligible overhead.
 *
 * <pre>{@code
 * WorkerPool<Order> pool = WorkerPool.<Order>builder()
 *     .workers(8)
 *     .queueSize(512)
 *     .handler((order) -> processOrder(order))
 *     .onError((order, err) -> log.error("failed", err))
 *     .build();
 * pool.start();
 * pool.submit(order);
 * pool.stop();
 * }</pre>
 */
public class WorkerPool<T> {

    private final int workers;
    private final int queueSize;
    private final JobHandler<T> handler;
    private final BiConsumer<T, Throwable> onError;

    private final BlockingQueue<T> queue;
    private final LongAdder submitted = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder errors = new LongAdder();

    private ExecutorService executor;
    private volatile boolean running;

    private WorkerPool(Builder<T> b) {
        this.workers = b.workers;
        this.queueSize = b.queueSize;
        this.handler = b.handler;
        this.onError = b.onError;
        this.queue = new LinkedBlockingQueue<>(queueSize);
    }

    /**
     * Starts the worker goroutines (virtual threads).
     * Must be called before {@link #submit}.
     */
    public void start() {
        running = true;
        // Java 21 virtual threads — lightweight like Go goroutines.
        executor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < workers; i++) {
            executor.submit(this::workerLoop);
        }
    }

    /**
     * Submits an item to the work queue, blocking if the queue is full.
     *
     * @param item the item to process
     * @throws InterruptedException if interrupted while waiting
     */
    public void submit(T item) throws InterruptedException {
        queue.put(item);
        submitted.increment();
    }

    /**
     * Attempts to submit an item without blocking.
     *
     * @param item the item to process
     * @return {@code true} if the item was enqueued, {@code false} if the queue is full
     */
    public boolean trySubmit(T item) {
        if (queue.offer(item)) { submitted.increment(); return true; }
        return false;
    }

    /**
     * Signals workers to stop and waits up to 30 seconds for completion.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void stop() throws InterruptedException {
        running = false;
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    /**
     * Returns a snapshot of pool metrics.
     *
     * @return current {@link PoolStats}
     */
    public PoolStats stats() {
        return new PoolStats(submitted.sum(), completed.sum(), errors.sum(), queue.size(), workers);
    }

    private void workerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                T item = queue.poll(100, TimeUnit.MILLISECONDS);
                if (item == null) continue;
                handler.handle(item);
                completed.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable e) {
                errors.increment();
                if (onError != null) onError.accept(null, e);
            }
        }
    }

    /** Functional interface for processing pool items. */
    @FunctionalInterface
    public interface JobHandler<T> {
        /**
         * Processes one item from the pool queue.
         *
         * @param item the item to process
         * @throws Exception if processing fails
         */
        void handle(T item) throws Exception;
    }

    /**
     * Snapshot of pool performance counters.
     *
     * @param submitted  total items ever submitted
     * @param completed  total items successfully processed
     * @param errors     total items that resulted in an error
     * @param queueDepth current number of items waiting in the queue
     * @param workers    total worker count
     */
    public record PoolStats(long submitted, long completed, long errors, int queueDepth, int workers) {}

    /**
     * Creates a new builder for a {@link WorkerPool}.
     *
     * @param <T> the item type
     * @return a fresh {@link Builder}
     */
    public static <T> Builder<T> builder() { return new Builder<>(); }

    /** Fluent builder for {@link WorkerPool}. */
    public static class Builder<T> {
        private int workers = Runtime.getRuntime().availableProcessors();
        private int queueSize = 256;
        private JobHandler<T> handler;
        private BiConsumer<T, Throwable> onError;

        /** Sets the number of worker goroutines (virtual threads). */
        public Builder<T> workers(int n) { this.workers = n; return this; }

        /** Sets the bounded queue capacity. */
        public Builder<T> queueSize(int n) { this.queueSize = n; return this; }

        /** Sets the item processing handler. */
        public Builder<T> handler(JobHandler<T> h) { this.handler = h; return this; }

        /** Sets an error callback invoked on handler failures. */
        public Builder<T> onError(BiConsumer<T, Throwable> fn) { this.onError = fn; return this; }

        /** Builds and returns the {@link WorkerPool}. */
        public WorkerPool<T> build() { return new WorkerPool<>(this); }
    }
}
