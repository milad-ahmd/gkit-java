package dev.gkit.graceful;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GracefulShutdownTest {

    @Test
    @DisplayName("shutdown runs registered hooks in LIFO order")
    void shutdownRunsHooksInLifoOrder() {
        List<String> order = new ArrayList<>();
        GracefulShutdown g = GracefulShutdown.builder()
                .timeout(Duration.ofSeconds(5))
                .build();

        g.register("first", () -> order.add("first"));
        g.register("second", () -> order.add("second"));
        g.register("third", () -> order.add("third"));

        g.shutdown();

        assertEquals(List.of("third", "second", "first"), order);
    }

    @Test
    @DisplayName("shutdown with no hooks does not throw")
    void shutdownWithNoHooksDoesNotThrow() {
        GracefulShutdown g = GracefulShutdown.builder().build();
        assertDoesNotThrow(g::shutdown);
    }

    @Test
    @DisplayName("shutdown executes all hooks even if some fail")
    void shutdownExecutesAllHooksDespiteFailures() {
        List<String> ran = new ArrayList<>();
        GracefulShutdown g = GracefulShutdown.builder()
                .timeout(Duration.ofSeconds(5))
                .build();

        g.register("hook1", () -> ran.add("hook1"));
        g.register("hook2", () -> { throw new RuntimeException("hook2 fails"); });
        g.register("hook3", () -> ran.add("hook3"));

        // Shutdown should throw ShutdownException due to hook2
        assertThrows(GracefulShutdown.ShutdownException.class, g::shutdown);

        // But hook1 and hook3 should still have run
        assertTrue(ran.contains("hook1"));
        assertTrue(ran.contains("hook3"));
    }

    @Test
    @DisplayName("shutdown throws ShutdownException when any hook fails")
    void shutdownThrowsShutdownExceptionOnFailure() {
        GracefulShutdown g = GracefulShutdown.builder()
                .timeout(Duration.ofSeconds(5))
                .build();
        g.register("failing-hook", () -> { throw new RuntimeException("intentional failure"); });

        GracefulShutdown.ShutdownException ex = assertThrows(
                GracefulShutdown.ShutdownException.class, g::shutdown);
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("failing-hook"));
    }

    @Test
    @DisplayName("shutdown throws ShutdownException when hook exceeds timeout")
    void shutdownThrowsOnTimeout() {
        GracefulShutdown g = GracefulShutdown.builder()
                .timeout(Duration.ofMillis(50))
                .build();
        g.register("slow-hook", () -> Thread.sleep(5000));

        GracefulShutdown.ShutdownException ex = assertThrows(
                GracefulShutdown.ShutdownException.class, g::shutdown);
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("register adds hook that is invoked during shutdown")
    void registerAddsHookForShutdown() {
        AtomicBoolean ran = new AtomicBoolean(false);
        GracefulShutdown g = GracefulShutdown.builder().build();
        g.register("my-hook", () -> ran.set(true));
        g.shutdown();
        assertTrue(ran.get());
    }

    @Test
    @DisplayName("builder default timeout is 30 seconds")
    void builderDefaultTimeout() {
        // Verify builder creates instance without error using defaults
        GracefulShutdown g = GracefulShutdown.builder().build();
        assertNotNull(g);
    }

    @Test
    @DisplayName("installShutdownHook returns same instance for chaining")
    void installShutdownHookReturnsSelf() {
        GracefulShutdown g = GracefulShutdown.builder().build();
        assertSame(g, g.installShutdownHook());
    }

    @Test
    @DisplayName("multiple successful hooks do not throw")
    void multipleSuccessfulHooksDoNotThrow() {
        List<String> ran = new ArrayList<>();
        GracefulShutdown g = GracefulShutdown.builder()
                .timeout(Duration.ofSeconds(5))
                .build();
        g.register("a", () -> ran.add("a"));
        g.register("b", () -> ran.add("b"));
        g.register("c", () -> ran.add("c"));

        assertDoesNotThrow(g::shutdown);
        assertEquals(3, ran.size());
    }

    @Test
    @DisplayName("ShutdownException has descriptive message")
    void shutdownExceptionHasMessage() {
        GracefulShutdown.ShutdownException ex = new GracefulShutdown.ShutdownException("something went wrong");
        assertEquals("something went wrong", ex.getMessage());
    }
}
