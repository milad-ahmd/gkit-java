package dev.gkit.retry;

import java.util.concurrent.Callable;

/**
 * Generic retry executor with configurable backoff and callbacks.
 *
 * <p>Supports typed return values via generics, abort-without-retry via
 * {@link StopException}, and void operations via {@link #executeVoid}.
 *
 * <pre>{@code
 * User user = Retry.execute(() -> db.findUser(id),
 *     RetryOptions.builder()
 *         .maxAttempts(5)
 *         .backoff(Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(10)).withJitter())
 *         .onRetry((attempt, err) -> log.warn("Retrying attempt {}: {}", attempt, err.getMessage()))
 *         .build());
 * }</pre>
 */
public final class Retry {

    private Retry() {}

    /**
     * Executes {@code fn} up to {@link RetryOptions#maxAttempts()} times.
     *
     * <p>On each failure the {@link RetryOptions#onRetry()} callback is invoked,
     * then the thread sleeps for {@link RetryOptions#backoff()} duration before
     * the next attempt. Throw {@link StopException} inside {@code fn} to abort
     * retrying immediately.
     *
     * @param <T>  the return type
     * @param fn   the callable to attempt
     * @param opts retry configuration
     * @return the result returned by the first successful invocation of {@code fn}
     * @throws MaxAttemptsExceededException if all attempts fail
     * @throws Exception                    if a {@link StopException} is thrown
     */
    public static <T> T execute(Callable<T> fn, RetryOptions opts) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < opts.maxAttempts(); attempt++) {
            try {
                return fn.call();
            } catch (StopException e) {
                throw e.getCause() instanceof Exception ex ? ex : new RuntimeException(e.getCause());
            } catch (Exception e) {
                last = e;
                opts.onRetry().accept(attempt + 1, e);
                if (attempt < opts.maxAttempts() - 1) {
                    Thread.sleep(opts.backoff().next(attempt).toMillis());
                }
            }
        }
        throw new MaxAttemptsExceededException(opts.maxAttempts(), last);
    }

    /**
     * Convenience overload for operations that return no value.
     *
     * @param fn   the runnable to attempt
     * @param opts retry configuration
     * @throws Exception on exhausted retries or abort
     */
    public static void executeVoid(ThrowingRunnable fn, RetryOptions opts) throws Exception {
        execute(() -> { fn.run(); return null; }, opts);
    }

    /**
     * Wraps an exception to signal that the retry loop should be aborted immediately.
     * The wrapped cause is re-thrown to the caller.
     *
     * @param cause the underlying exception
     * @return a {@link StopException} wrapping {@code cause}
     */
    public static StopException stop(Throwable cause) { return new StopException(cause); }

    /** A runnable that may throw a checked exception. */
    @FunctionalInterface
    public interface ThrowingRunnable { void run() throws Exception; }
}
