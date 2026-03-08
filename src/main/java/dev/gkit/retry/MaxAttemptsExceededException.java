package dev.gkit.retry;

/**
 * Thrown by {@link Retry#execute} when all configured attempts have been exhausted.
 *
 * <p>The {@link #getCause()} returns the last exception thrown by the callable.
 */
public class MaxAttemptsExceededException extends Exception {

    private final int maxAttempts;

    /**
     * Creates a new exception.
     *
     * @param maxAttempts the configured maximum attempt count
     * @param cause       the last exception thrown by the callable
     */
    public MaxAttemptsExceededException(int maxAttempts, Throwable cause) {
        super("Max attempts (" + maxAttempts + ") exceeded", cause);
        this.maxAttempts = maxAttempts;
    }

    /** Returns the maximum number of attempts that were configured. */
    public int getMaxAttempts() { return maxAttempts; }
}
