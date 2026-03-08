package dev.gkit.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.function.ToDoubleFunction;

/**
 * Prometheus/Micrometer metrics helpers.
 *
 * <pre>{@code
 * Metrics.Registry reg = new Metrics.Registry("myapp", meterRegistry);
 * Counter requests = reg.counter("http_requests_total", "method", "GET", "path", "/api");
 * requests.increment();
 * }</pre>
 */
public final class Metrics {

    private Metrics() {}

    public static final class Registry {
        private final String namespace;
        private final MeterRegistry registry;

        public Registry(String namespace, MeterRegistry registry) {
            this.namespace = namespace;
            this.registry = registry;
        }

        public Registry(String namespace) {
            this(namespace, new SimpleMeterRegistry());
        }

        public MeterRegistry getMeterRegistry() { return registry; }

        public Counter counter(String name, String... tags) {
            return Counter.builder(namespace + "_" + name)
                .tags(tags).register(registry);
        }

        public Gauge gauge(String name, Object obj, ToDoubleFunction<Object> fn, String... tags) {
            return Gauge.builder(namespace + "_" + name, obj, fn)
                .tags(tags).register(registry);
        }

        public Timer timer(String name, String... tags) {
            return Timer.builder(namespace + "_" + name)
                .tags(tags).register(registry);
        }

        public DistributionSummary summary(String name, String... tags) {
            return DistributionSummary.builder(namespace + "_" + name)
                .tags(tags).register(registry);
        }

        /** Records duration of a runnable. */
        public void record(String name, Runnable r, String... tags) {
            timer(name, tags).record(r);
        }
    }
}
