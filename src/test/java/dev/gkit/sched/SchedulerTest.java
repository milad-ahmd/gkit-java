package dev.gkit.sched;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    @Test
    @DisplayName("after schedules a one-shot job that runs after the delay")
    void afterSchedulesOneShotJob() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Scheduler sched = Scheduler.builder()
                .workers(1)
                .build();
        sched.after(Duration.ofMillis(50), "one-shot", latch::countDown);
        sched.start();

        boolean ran = latch.await(2, TimeUnit.SECONDS);
        sched.stop();
        assertTrue(ran, "one-shot job should have run");
    }

    @Test
    @DisplayName("every schedules a repeating job that runs multiple times")
    void everySchedulesRepeatingJob() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        Scheduler sched = Scheduler.builder()
                .workers(1)
                .build();
        sched.every(Duration.ofMillis(50), "repeating", latch::countDown);
        sched.start();

        boolean ran = latch.await(5, TimeUnit.SECONDS);
        sched.stop();
        assertTrue(ran, "repeating job should have run at least 3 times");
    }

    @Test
    @DisplayName("stop waits for jobs to finish and prevents new runs")
    void stopPreventsNewRuns() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        Scheduler sched = Scheduler.builder().workers(1).build();
        sched.every(Duration.ofMillis(100), "job", count::incrementAndGet);
        sched.start();
        Thread.sleep(250); // let it run a couple of times
        sched.stop();
        int countAtStop = count.get();
        Thread.sleep(200); // wait longer — no new runs should happen
        assertEquals(countAtStop, count.get(), "no new executions after stop");
    }

    @Test
    @DisplayName("onError callback is invoked when job throws")
    void onErrorCalledWhenJobThrows() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> caughtError = new AtomicReference<>();

        Scheduler sched = Scheduler.builder()
                .workers(1)
                .onError((job, err) -> {
                    caughtError.set(err);
                    latch.countDown();
                })
                .build();

        sched.after(Duration.ofMillis(10), "fail-job", () -> {
            throw new RuntimeException("intentional error");
        });
        sched.start();

        assertTrue(latch.await(3, TimeUnit.SECONDS), "onError should have been called");
        sched.stop();
        assertNotNull(caughtError.get());
    }

    @Test
    @DisplayName("multiple jobs are scheduled independently")
    void multipleJobsScheduledIndependently() throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        Scheduler sched = Scheduler.builder().workers(2).build();
        sched.after(Duration.ofMillis(30), "job1", latch1::countDown);
        sched.after(Duration.ofMillis(60), "job2", latch2::countDown);
        sched.start();

        assertTrue(latch1.await(2, TimeUnit.SECONDS));
        assertTrue(latch2.await(2, TimeUnit.SECONDS));
        sched.stop();
    }

    @Test
    @DisplayName("Scheduler.Job record exposes name and fn")
    void jobRecordExposesNameAndFn() {
        Runnable fn = () -> {};
        Scheduler.Job job = new Scheduler.Job("my-job", fn);
        assertEquals("my-job", job.name());
        assertSame(fn, job.fn());
    }

    @Test
    @DisplayName("builder creates scheduler with custom workers")
    void builderCreatesSchedulerWithCustomWorkers() throws InterruptedException {
        // Just verify it starts and stops without error
        Scheduler sched = Scheduler.builder().workers(4).build();
        sched.start();
        sched.stop();
    }

    @Test
    @DisplayName("stop without start does not throw")
    void stopWithoutStartDoesNotThrow() {
        Scheduler sched = Scheduler.builder().build();
        assertDoesNotThrow(sched::stop);
    }
}
