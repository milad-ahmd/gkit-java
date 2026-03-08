package dev.gkit.async;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
/** Counting semaphore controlling access to a shared resource pool. */
public final class Semaphore {
    private final java.util.concurrent.Semaphore inner;
    private final int capacity;
    public Semaphore(int permits) {
        if (permits <= 0) throw new IllegalArgumentException("permits must be positive");
        this.capacity = permits;
        this.inner = new java.util.concurrent.Semaphore(permits, true);
    }
    /** Acquires one permit, blocking until one is available or interrupted. */
    public void acquire() throws InterruptedException { inner.acquire(); }
    /** Non-blocking acquire: returns true if a permit was available. */
    public boolean tryAcquire() { return inner.tryAcquire(); }
    /** Acquires with timeout. Returns true if acquired within timeout. */
    public boolean tryAcquire(Duration timeout) throws InterruptedException {
        return inner.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }
    /** Releases one permit back to the pool. */
    public void release() { inner.release(); }
    /** Returns the number of currently available permits. */
    public int available() { return inner.availablePermits(); }
    /** Returns the total permit capacity. */
    public int capacity() { return capacity; }
}
