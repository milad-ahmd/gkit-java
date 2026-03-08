package dev.gkit.circuitbreaker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private CircuitBreaker breaker(int failureThreshold, int successThreshold, Duration openTimeout) {
        return CircuitBreaker.builder()
                .failureThreshold(failureThreshold)
                .successThreshold(successThreshold)
                .openTimeout(openTimeout)
                .build();
    }

    @Test
    @DisplayName("starts in CLOSED state")
    void startsInClosedState() {
        CircuitBreaker cb = breaker(3, 1, Duration.ofSeconds(60));
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }

    @Test
    @DisplayName("execute returns result in CLOSED state")
    void executeReturnsResultWhenClosed() throws Exception {
        CircuitBreaker cb = breaker(3, 1, Duration.ofSeconds(60));
        String result = cb.execute(() -> "success");
        assertEquals("success", result);
    }

    @Test
    @DisplayName("transitions to OPEN after failureThreshold failures")
    void transitionsToOpenAfterThreshold() {
        CircuitBreaker cb = breaker(3, 1, Duration.ofSeconds(60));
        for (int i = 0; i < 3; i++) {
            try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.state());
    }

    @Test
    @DisplayName("throws OpenException when circuit is OPEN")
    void throwsOpenExceptionWhenOpen() {
        CircuitBreaker cb = breaker(1, 1, Duration.ofSeconds(60));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        assertThrows(CircuitBreaker.OpenException.class, () -> cb.execute(() -> "should not reach"));
    }

    @Test
    @DisplayName("transitions to HALF_OPEN after openTimeout elapses")
    void transitionsToHalfOpenAfterTimeout() throws InterruptedException {
        CircuitBreaker cb = breaker(1, 1, Duration.ofMillis(100));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        assertEquals(CircuitBreaker.State.OPEN, cb.state());
        Thread.sleep(150);
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.state());
    }

    @Test
    @DisplayName("transitions from HALF_OPEN to CLOSED on successThreshold successes")
    void halfOpenToClosedOnSuccess() throws Exception {
        CircuitBreaker cb = breaker(1, 1, Duration.ofMillis(50));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        Thread.sleep(100);
        // probe succeeds — should close
        cb.execute(() -> "probe");
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }

    @Test
    @DisplayName("transitions from HALF_OPEN back to OPEN on failure")
    void halfOpenBackToOpenOnFailure() throws InterruptedException {
        CircuitBreaker cb = breaker(1, 2, Duration.ofMillis(50));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        Thread.sleep(100);
        // probe fails — should re-open
        try { cb.execute(() -> { throw new RuntimeException("probe fail"); }); } catch (Exception ignored) {}
        assertEquals(CircuitBreaker.State.OPEN, cb.state());
    }

    @Test
    @DisplayName("run() convenience method works for void operations")
    void runMethodWorksForVoidOperations() {
        CircuitBreaker cb = breaker(3, 1, Duration.ofSeconds(60));
        AtomicInteger counter = new AtomicInteger(0);
        assertDoesNotThrow(() -> cb.run(counter::incrementAndGet));
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("reset() returns breaker to CLOSED state")
    void resetReturnsToClosedState() {
        CircuitBreaker cb = breaker(1, 1, Duration.ofSeconds(60));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        assertEquals(CircuitBreaker.State.OPEN, cb.state());
        cb.reset();
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }

    @Test
    @DisplayName("reset() allows executions to succeed again")
    void resetAllowsSuccessfulExecution() throws Exception {
        CircuitBreaker cb = breaker(1, 1, Duration.ofSeconds(60));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        cb.reset();
        String result = cb.execute(() -> "ok after reset");
        assertEquals("ok after reset", result);
    }

    @Test
    @DisplayName("onStateChange callback is invoked on transitions")
    void onStateChangeCallbackInvoked() {
        List<String> transitions = new ArrayList<>();
        CircuitBreaker cb = CircuitBreaker.builder()
                .failureThreshold(1)
                .successThreshold(1)
                .openTimeout(Duration.ofMillis(50))
                .onStateChange((from, to) -> transitions.add(from + "->" + to))
                .build();

        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        assertTrue(transitions.contains("CLOSED->OPEN"));
    }

    @Test
    @DisplayName("failure counter resets on successful execution in CLOSED state")
    void failureCounterResetsOnSuccess() throws Exception {
        CircuitBreaker cb = breaker(3, 1, Duration.ofSeconds(60));
        // Accumulate 2 failures
        for (int i = 0; i < 2; i++) {
            try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        }
        // Succeed — should reset failure counter
        cb.execute(() -> "ok");
        // Two more failures should not trip the breaker (counter was reset to 0)
        for (int i = 0; i < 2; i++) {
            try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        }
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }

    @Test
    @DisplayName("successThreshold of 2 requires two probes to close from HALF_OPEN")
    void successThresholdOfTwoRequiresTwoProbes() throws Exception {
        CircuitBreaker cb = breaker(1, 2, Duration.ofMillis(50));
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        Thread.sleep(100);

        // First probe — still HALF_OPEN
        cb.execute(() -> "probe1");
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.state());

        // Second probe — now CLOSED
        cb.execute(() -> "probe2");
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }
}
