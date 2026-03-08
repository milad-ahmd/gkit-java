package dev.gkit.retry;

/**
 * Thrown inside a retry callback to abort retrying immediately.
 *
 * <p>When {@link Retry#execute} catches a {@link StopException} it stops
 * further attempts and re-throws the wrapped {@link #getCause()}.
 * Use {@link Retry#stop(Throwable)} to create instances.
 *
 * <pre>{@code
 * Retry.execute(() -> {
 *     try {
 *         return db.query(sql);
 *     } catch (AccessDeniedException e) {
 *         throw Retry.stop(e); // no point retrying
 *     }
 * }, opts);
 * }</pre>
 */
public class StopException extends RuntimeException {

    /**
     * Creates a new {@link StopException} wrapping the given cause.
     *
     * @param cause the underlying exception that should be re-thrown
     */
    public StopException(Throwable cause) { super(cause); }
}
