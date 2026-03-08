package dev.gkit.pipeline;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Generic concurrent data-processing pipeline.
 *
 * <pre>{@code
 * // Fan-out: process items concurrently
 * List<Image> resized = Pipeline.process(imageUrls, url -> downloadAndResize(url), 8);
 *
 * // Sequential chain
 * Pipeline.Stage<String, Integer> chain = Pipeline.chain(Integer::parseInt, n -> n * 2);
 * int result = chain.apply("21"); // 42
 * }</pre>
 */
public final class Pipeline {

    private Pipeline() {}

    @FunctionalInterface
    public interface Stage<In, Out> {
        Out apply(In in) throws Exception;
    }

    /**
     * Processes items concurrently with the given number of workers.
     * Results are returned in input order.
     */
    public static <In, Out> List<Out> process(List<In> items, Stage<In, Out> fn, int workers) throws Exception {
        if (items.isEmpty()) return Collections.emptyList();
        int w = Math.min(workers > 0 ? workers : items.size(), items.size());

        ExecutorService exec = Executors.newFixedThreadPool(w);
        List<Future<Out>> futures = new ArrayList<>(items.size());
        try {
            for (In item : items) {
                futures.add(exec.submit(() -> fn.apply(item)));
            }
            List<Out> results = new ArrayList<>(items.size());
            for (Future<Out> f : futures) {
                results.add(f.get());
            }
            return results;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        } finally {
            exec.shutdownNow();
        }
    }

    /** Chains same-type stages sequentially. */
    @SafeVarargs
    public static <T> Stage<T, T> chain(Stage<T, T>... stages) {
        return in -> {
            T current = in;
            for (Stage<T, T> stage : stages) {
                current = stage.apply(current);
            }
            return current;
        };
    }

    /** Composes two typed stages: A → B → C. */
    public static <A, B, C> Stage<A, C> compose(Stage<A, B> first, Stage<B, C> second) {
        return in -> second.apply(first.apply(in));
    }

    /** Fluent builder for sequential pipelines. */
    public static <T> Builder<T> of(T input) { return new Builder<>(input); }

    public static final class Builder<T> {
        private T value;

        Builder(T value) { this.value = value; }

        public <U> Builder<U> then(Stage<T, U> stage) throws Exception {
            return new Builder<>(stage.apply(value));
        }

        public T get() { return value; }
    }
}
