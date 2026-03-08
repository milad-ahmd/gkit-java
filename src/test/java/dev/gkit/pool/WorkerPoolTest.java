package dev.gkit.pool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkerPoolTest {

    @Test
    @DisplayName("submitted items are processed by handler")
    void itemsAreProcessed() throws InterruptedException {
        CopyOnWriteArrayList<String> processed = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        WorkerPool<String> pool = WorkerPool.<String>builder()
                .workers(2)
                .queueSize(10)
                .handler(item -> {
                    processed.add(item);
                    latch.countDown();
                })
                .build();

        pool.start();
        pool.submit("a");
        pool.submit("b");
        pool.submit("c");

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Not all items processed in time");
        pool.stop();

        assertEquals(3, processed.size());
        assertTrue(processed.containsAll(java.util.List.of("a", "b", "c")));
    }

    @Test
    @DisplayName("stats show correct submitted and completed counts")
    void statsTrackSubmittedAndCompleted() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        WorkerPool<Integer> pool = WorkerPool.<Integer>builder()
                .workers(1)
                .queueSize(10)
                .handler(item -> latch.countDown())
                .build();

        pool.start();
        pool.submit(1);
        pool.submit(2);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.stop();

        WorkerPool.PoolStats stats = pool.stats();
        assertEquals(2, stats.submitted());
        assertEquals(2, stats.completed());
        assertEquals(0, stats.errors());
    }

    @Test
    @DisplayName("trySubmit returns false when queue is full")
    void trySubmitReturnsFalseWhenQueueFull() throws InterruptedException {
        CountDownLatch block = new CountDownLatch(1);
        WorkerPool<Integer> pool = WorkerPool.<Integer>builder()
                .workers(1)
                .queueSize(1)
                .handler(item -> block.await())
                .build();

        pool.start();
        // Fill the queue and tie up the worker
        pool.submit(1); // worker picks this up
        Thread.sleep(50); // let the worker take the item
        pool.trySubmit(2); // fill the queue

        // Queue is now at capacity — next trySubmit should fail
        boolean result = pool.trySubmit(3);
        assertFalse(result);

        block.countDown();
        pool.stop();
    }

    @Test
    @DisplayName("onError callback is invoked when handler throws")
    void onErrorCalledOnHandlerException() throws InterruptedException {
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        WorkerPool<String> pool = WorkerPool.<String>builder()
                .workers(1)
                .queueSize(10)
                .handler(item -> { throw new RuntimeException("handler error"); })
                .onError((item, err) -> {
                    caughtError.set(err);
                    latch.countDown();
                })
                .build();

        pool.start();
        pool.submit("trigger-error");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.stop();

        assertNotNull(caughtError.get());
        assertEquals(1, pool.stats().errors());
    }

    @Test
    @DisplayName("stats workers field reflects configured workers")
    void statsWorkersMatchesConfiguration() throws InterruptedException {
        WorkerPool<Integer> pool = WorkerPool.<Integer>builder()
                .workers(4)
                .queueSize(10)
                .handler(item -> {})
                .build();

        pool.start();
        assertEquals(4, pool.stats().workers());
        pool.stop();
    }

    @Test
    @DisplayName("pool processes items with multiple concurrent workers")
    void multipleConcurrentWorkers() throws InterruptedException {
        int itemCount = 20;
        CountDownLatch latch = new CountDownLatch(itemCount);
        AtomicInteger processed = new AtomicInteger(0);

        WorkerPool<Integer> pool = WorkerPool.<Integer>builder()
                .workers(4)
                .queueSize(50)
                .handler(item -> {
                    processed.incrementAndGet();
                    latch.countDown();
                })
                .build();

        pool.start();
        for (int i = 0; i < itemCount; i++) {
            pool.submit(i);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.stop();

        assertEquals(itemCount, processed.get());
    }

    @Test
    @DisplayName("queueDepth decreases as items are consumed")
    void queueDepthDecreasesAsItemsConsumed() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        WorkerPool<Integer> pool = WorkerPool.<Integer>builder()
                .workers(1)
                .queueSize(10)
                .handler(item -> latch.countDown())
                .build();

        pool.start();
        pool.submit(42);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.stop();

        // After stop, queue should be empty
        assertEquals(0, pool.stats().queueDepth());
    }
}
