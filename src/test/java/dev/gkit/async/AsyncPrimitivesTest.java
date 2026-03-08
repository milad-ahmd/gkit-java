package dev.gkit.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPrimitivesTest {

    // -----------------------------------------------------------------------
    // Future<T>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Future.completed returns value immediately")
    void futureCompletedReturnsValue() throws Exception {
        Future<String> f = Future.completed("hello");
        assertTrue(f.isDone());
        assertEquals("hello", f.get());
    }

    @Test
    @DisplayName("Future.async executes callable asynchronously")
    void futureAsyncExecutesCallable() throws Exception {
        Future<Integer> f = Future.async(() -> 42);
        assertEquals(42, f.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Future.failed completes exceptionally")
    void futureFailedIsExceptional() {
        RuntimeException cause = new RuntimeException("boom");
        Future<String> f = Future.failed(cause);
        ExecutionException ex = assertThrows(ExecutionException.class, f::get);
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("Future.map transforms the result")
    void futureMapTransformsResult() throws Exception {
        Future<String> f = Future.completed("world").map(s -> "hello " + s);
        assertEquals("hello world", f.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Future.flatMap chains futures")
    void futureFlatMapChains() throws Exception {
        Future<Integer> f = Future.completed(5).flatMap(n -> Future.completed(n * 2));
        assertEquals(10, f.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Future.all waits for all and returns results")
    void futureAllWaitsForAll() throws Exception {
        Future<Integer> f1 = Future.completed(1);
        Future<Integer> f2 = Future.completed(2);
        Future<Integer> f3 = Future.completed(3);
        List<Integer> results = Future.all(f1, f2, f3).get(5, TimeUnit.SECONDS);
        assertEquals(3, results.size());
        assertTrue(results.containsAll(List.of(1, 2, 3)));
    }

    @Test
    @DisplayName("Future.race returns the first to complete")
    void futureRaceReturnsFirst() throws Exception {
        Future<String> fast = Future.completed("fast");
        Future<String> slow = Future.async(() -> {
            Thread.sleep(1000);
            return "slow";
        });
        String winner = Future.race(fast, slow).get(5, TimeUnit.SECONDS);
        assertEquals("fast", winner);
    }

    @Test
    @DisplayName("Future.getNow returns null for incomplete future")
    void futureGetNowNullIfNotDone() {
        Future<String> f = Future.async(() -> {
            Thread.sleep(5000);
            return "late";
        });
        assertNull(f.getNow());
    }

    @Test
    @DisplayName("Future.toCompletableFuture exposes delegate")
    void futureToCompletableFuture() {
        Future<String> f = Future.completed("x");
        assertNotNull(f.toCompletableFuture());
        assertEquals("x", f.toCompletableFuture().join());
    }

    // -----------------------------------------------------------------------
    // Semaphore
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Semaphore constructor rejects non-positive permits")
    void semaphoreRejectsNonPositivePermits() {
        assertThrows(IllegalArgumentException.class, () -> new Semaphore(0));
        assertThrows(IllegalArgumentException.class, () -> new Semaphore(-1));
    }

    @Test
    @DisplayName("Semaphore capacity returns configured value")
    void semaphoreCapacityMatchesConstructor() {
        Semaphore s = new Semaphore(5);
        assertEquals(5, s.capacity());
    }

    @Test
    @DisplayName("Semaphore available decreases on acquire and increases on release")
    void semaphoreAvailableTracksAcquireRelease() throws InterruptedException {
        Semaphore s = new Semaphore(3);
        assertEquals(3, s.available());
        s.acquire();
        assertEquals(2, s.available());
        s.acquire();
        assertEquals(1, s.available());
        s.release();
        assertEquals(2, s.available());
    }

    @Test
    @DisplayName("Semaphore tryAcquire returns true when permit available")
    void semaphoreTryAcquireSucceeds() {
        Semaphore s = new Semaphore(1);
        assertTrue(s.tryAcquire());
        assertEquals(0, s.available());
    }

    @Test
    @DisplayName("Semaphore tryAcquire returns false when no permits available")
    void semaphoreTryAcquireFailsWhenExhausted() {
        Semaphore s = new Semaphore(1);
        assertTrue(s.tryAcquire());
        assertFalse(s.tryAcquire());
    }

    @Test
    @DisplayName("Semaphore tryAcquire with timeout acquires if permit becomes available")
    void semaphoreTryAcquireWithTimeout() throws InterruptedException {
        Semaphore s = new Semaphore(1);
        s.acquire(); // exhaust permits

        // Release in a background thread after short delay
        Thread releaser = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            s.release();
        });
        releaser.start();

        boolean acquired = s.tryAcquire(Duration.ofMillis(500));
        assertTrue(acquired);
        releaser.join();
    }

    // -----------------------------------------------------------------------
    // Stream<T>
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Stream.fromList collects all items in order")
    void streamFromListCollectsAll() throws InterruptedException {
        List<Integer> items = List.of(1, 2, 3, 4, 5);
        Stream<Integer> stream = Stream.fromList(items);
        List<Integer> result = stream.collect();
        assertEquals(items, result);
    }

    @Test
    @DisplayName("Stream.map transforms each element")
    void streamMapTransformsElements() throws InterruptedException {
        Stream<Integer> stream = Stream.fromList(List.of(1, 2, 3));
        List<Integer> result = stream.map(n -> n * 10).collect();
        assertEquals(List.of(10, 20, 30), result);
    }

    @Test
    @DisplayName("Stream.filter keeps only matching elements")
    void streamFilterKeepsMatchingElements() throws InterruptedException {
        Stream<Integer> stream = Stream.fromList(List.of(1, 2, 3, 4, 5));
        List<Integer> result = stream.filter(n -> n % 2 == 0).collect();
        assertEquals(List.of(2, 4), result);
    }

    @Test
    @DisplayName("Stream.take limits to n items")
    void streamTakeLimitsItems() throws InterruptedException {
        Stream<Integer> stream = Stream.fromList(List.of(1, 2, 3, 4, 5));
        List<Integer> result = stream.take(3).collect();
        assertEquals(3, result.size());
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    @DisplayName("Stream.fromList with empty list produces empty stream")
    void streamFromEmptyList() throws InterruptedException {
        Stream<String> stream = Stream.fromList(List.of());
        List<String> result = stream.collect();
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Stream.fanIn merges multiple streams")
    void streamFanInMergesStreams() throws InterruptedException {
        Stream<Integer> s1 = Stream.fromList(List.of(1, 2));
        Stream<Integer> s2 = Stream.fromList(List.of(3, 4));
        List<Integer> result = Stream.fanIn(s1, s2).collect();
        assertEquals(4, result.size());
        assertTrue(result.containsAll(List.of(1, 2, 3, 4)));
    }

    @Test
    @DisplayName("Stream.forEach visits each element")
    void streamForEachVisitsEachElement() throws InterruptedException {
        AtomicBoolean visited = new AtomicBoolean(false);
        Stream<String> stream = Stream.fromList(List.of("hello"));
        stream.forEach(item -> {
            assertEquals("hello", item);
            visited.set(true);
        });
        assertTrue(visited.get());
    }
}
