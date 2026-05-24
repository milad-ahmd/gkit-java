package dev.gkit.async;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
/**
 * Stream<T> is a lazy, push-based async stream with backpressure.
 * Operators (map, filter, batch, take) each create a new stage.
 * @param <T> element type
 */
public final class Stream<T> {
    private final BlockingQueue<Object> queue;
    private static final Object END = new Object();
    private static final int DEFAULT_BUFFER = 16;

    private Stream(BlockingQueue<Object> queue) { this.queue = queue; }

    /** Creates a Stream from a producer function. */
    public static <T> Stream<T> generate(Consumer<Consumer<T>> producer) {
        BlockingQueue<Object> q = new LinkedBlockingQueue<>(DEFAULT_BUFFER);
        CompletableFuture.runAsync(() -> {
            try {
                producer.accept(item -> { try { q.put(item); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            } finally {
                try { q.put(END); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        return new Stream<>(q);
    }

    /** Creates a Stream that emits all values from the list. */
    public static <T> Stream<T> fromList(List<T> items) {
        return generate(send -> items.forEach(send));
    }

    /** Drains the stream and returns all items as a list. */
    @SuppressWarnings("unchecked")
    public List<T> collect() throws InterruptedException {
        List<T> out = new ArrayList<>();
        Object item;
        while ((item = queue.take()) != END) out.add((T) item);
        return out;
    }

    /** Calls fn for each item in the stream. */
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<T> fn) throws InterruptedException {
        Object item;
        while ((item = queue.take()) != END) fn.accept((T) item);
    }

    /** Returns a new Stream that applies fn to each element. */
    public <U> Stream<U> map(Function<T, U> fn) {
        return generate(send -> { try { forEach(item -> send.accept(fn.apply(item))); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
    }

    /** Returns a new Stream passing only items for which predicate returns true. */
    public Stream<T> filter(Predicate<T> predicate) {
        return generate(send -> { try { forEach(item -> { if (predicate.test(item)) send.accept(item); }); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
    }

    /** Returns a new Stream emitting at most n items. */
    public Stream<T> take(int n) {
        return generate(send -> {
            int[] count = {0};
            try { forEach(item -> { if (count[0]++ < n) send.accept(item); }); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }

    /**
     * Accumulates items into batches of at most size, or flushes after timeout
     * since the last item was received.
     */
    public Stream<List<T>> batch(int size, Duration timeout) {
        return generate(send -> {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            List<T> buf = Collections.synchronizedList(new ArrayList<>());
            Runnable flush = () -> { synchronized (buf) { if (!buf.isEmpty()) { send.accept(new ArrayList<>(buf)); buf.clear(); } } };
            ScheduledFuture<?> timer = sched.scheduleAtFixedRate(flush, timeout.toMillis(), timeout.toMillis(), TimeUnit.MILLISECONDS);
            try {
                forEach(item -> { buf.add(item); if (buf.size() >= size) flush.run(); });
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { timer.cancel(false); sched.shutdown(); flush.run(); }
        });
    }

    /** Broadcasts all items from in to n independent queues (fan-out). */
    @SuppressWarnings("unchecked")
    public List<Stream<T>> fanOut(int n) {
        List<BlockingQueue<Object>> qs = new ArrayList<>();
        for (int i=0; i<n; i++) qs.add(new LinkedBlockingQueue<>(DEFAULT_BUFFER));
        CompletableFuture.runAsync(() -> {
            try {
                Object item;
                while ((item = queue.take()) != END) { for (var q : qs) q.put(item); }
                for (var q : qs) q.put(END);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        List<Stream<T>> streams = new ArrayList<>();
        for (var q : qs) streams.add(new Stream<>(q));
        return streams;
    }

    /** Merges multiple streams into one (fan-in). */
    @SafeVarargs
    public static <T> Stream<T> fanIn(Stream<T>... streams) {
        BlockingQueue<Object> out = new LinkedBlockingQueue<>(DEFAULT_BUFFER);
        java.util.concurrent.atomic.AtomicInteger latch = new java.util.concurrent.atomic.AtomicInteger(streams.length);
        for (Stream<T> s : streams) {
            CompletableFuture.runAsync(() -> {
                try { s.forEach(item -> { try { out.put(item); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { if (latch.decrementAndGet() == 0) { try { out.put(END); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } } }
            });
        }
        return new Stream<>(out);
    }
}
