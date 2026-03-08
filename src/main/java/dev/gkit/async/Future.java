package dev.gkit.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/** Future<T>: asynchronous computation handle built on CompletableFuture. */
public final class Future<T> {
    private final CompletableFuture<T> delegate;
    private Future(CompletableFuture<T> d) { this.delegate = d; }
    public static <T> Future<T> async(Callable<T> fn) {
        return new Future<>(CompletableFuture.supplyAsync(() -> {
            try { return fn.call(); } catch (Exception e) { throw new CompletionException(e); }
        }));
    }
    public static <T> Future<T> completed(T v) { return new Future<>(CompletableFuture.completedFuture(v)); }
    public static <T> Future<T> failed(Throwable t) {
        CompletableFuture<T> cf = new CompletableFuture<>(); cf.completeExceptionally(t); return new Future<>(cf);
    }
    public T get() throws ExecutionException, InterruptedException { return delegate.get(); }
    public T get(long t, TimeUnit u) throws ExecutionException, InterruptedException, TimeoutException { return delegate.get(t, u); }
    public T getNow() { return delegate.getNow(null); }
    public boolean isDone() { return delegate.isDone(); }
    public <U> Future<U> map(Function<T,U> fn) { return new Future<>(delegate.thenApply(fn)); }
    public <U> Future<U> flatMap(Function<T,Future<U>> fn) { return new Future<>(delegate.thenCompose(v -> fn.apply(v).delegate)); }
    public CompletableFuture<T> toCompletableFuture() { return delegate; }
    @SafeVarargs
    public static <T> Future<List<T>> all(Future<T>... futures) {
        @SuppressWarnings("unchecked") CompletableFuture<T>[] cfs = new CompletableFuture[futures.length];
        for (int i=0;i<futures.length;i++) cfs[i]=futures[i].delegate;
        return new Future<>(CompletableFuture.allOf(cfs).thenApply(v -> {
            List<T> r = new ArrayList<>(cfs.length);
            for (CompletableFuture<T> cf : cfs) r.add(cf.join());
            return r;
        }));
    }
    @SafeVarargs @SuppressWarnings("unchecked")
    public static <T> Future<T> race(Future<T>... futures) {
        CompletableFuture<T>[] cfs = new CompletableFuture[futures.length];
        for (int i=0;i<futures.length;i++) cfs[i]=futures[i].delegate;
        return new Future<>((CompletableFuture<T>) CompletableFuture.anyOf(cfs));
    }
}
