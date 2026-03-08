package dev.gkit.pubsub;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Typed in-process publish/subscribe event bus.
 *
 * <pre>{@code
 * PubSub.Bus bus = new PubSub.Bus();
 *
 * Runnable unsub = PubSub.subscribe(bus, "orders.placed", (OrderPlaced event) -> {
 *     processOrder(event);
 * });
 *
 * PubSub.publish(bus, "orders.placed", new OrderPlaced("order-123"));
 * unsub.run(); // unsubscribe
 * }</pre>
 */
public final class PubSub {

    private PubSub() {}

    @FunctionalInterface
    public interface Handler<T> {
        void handle(T event) throws Exception;
    }

    public static final class Bus {
        private final Map<String, List<Subscription<?>>> subs = new ConcurrentHashMap<>();
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        public <T> Runnable subscribe(String topic, Handler<T> handler) {
            Subscription<T> sub = new Subscription<>(handler);
            subs.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(sub);
            return () -> {
                List<Subscription<?>> list = subs.get(topic);
                if (list != null) list.remove(sub);
                sub.cancelled = true;
            };
        }

        @SuppressWarnings("unchecked")
        public <T> void publish(String topic, T payload) {
            List<Subscription<?>> handlers = subs.getOrDefault(topic, Collections.emptyList());
            for (Subscription<?> sub : handlers) {
                if (!sub.cancelled) {
                    Subscription<T> typed = (Subscription<T>) sub;
                    executor.submit(() -> {
                        try { typed.handler.handle(payload); }
                        catch (Exception ignored) {}
                    });
                }
            }
        }

        public Set<String> topics() { return Collections.unmodifiableSet(subs.keySet()); }

        public void shutdown() { executor.shutdown(); }
    }

    private static class Subscription<T> {
        final Handler<T> handler;
        volatile boolean cancelled = false;

        Subscription(Handler<T> handler) { this.handler = handler; }
    }

    /** Top-level subscribe helper. */
    public static <T> Runnable subscribe(Bus bus, String topic, Handler<T> handler) {
        return bus.subscribe(topic, handler);
    }

    /** Top-level publish helper. */
    public static <T> void publish(Bus bus, String topic, T payload) {
        bus.publish(topic, payload);
    }
}
