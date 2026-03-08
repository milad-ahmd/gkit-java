package dev.gkit.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.api.common.Attributes;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * OpenTelemetry setup helpers.
 *
 * <pre>{@code
 * Tracer.install(Tracer.Config.builder()
 *     .serviceName("my-service")
 *     .otlpEndpoint("http://localhost:4317")
 *     .build());
 *
 * try (Scope scope = Tracer.startSpan("my-operation")) {
 *     doWork();
 * }
 * }</pre>
 */
public final class Tracer {

    private Tracer() {}

    public static final class Config {
        private final String serviceName;
        private final String otlpEndpoint;
        private final Duration exportTimeout;

        private Config(Builder b) {
            this.serviceName   = b.serviceName;
            this.otlpEndpoint  = b.otlpEndpoint;
            this.exportTimeout = b.exportTimeout;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String serviceName  = "gkit-service";
            private String otlpEndpoint = null;
            private Duration exportTimeout = Duration.ofSeconds(10);

            public Builder serviceName(String v)     { this.serviceName = v;   return this; }
            public Builder otlpEndpoint(String v)    { this.otlpEndpoint = v;  return this; }
            public Builder exportTimeout(Duration v) { this.exportTimeout = v; return this; }
            public Config build()                    { return new Config(this); }
        }
    }

    /** Installs a global OpenTelemetry TracerProvider from the given config. */
    public static OpenTelemetrySdk install(Config cfg) {
        Resource resource = Resource.getDefault().toBuilder()
            .put(ResourceAttributes.SERVICE_NAME, cfg.serviceName)
            .build();

        SdkTracerProvider.Builder tracerBuilder = SdkTracerProvider.builder()
            .setResource(resource);

        if (cfg.otlpEndpoint != null) {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(cfg.otlpEndpoint)
                .setTimeout(cfg.exportTimeout)
                .build();
            tracerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
        }

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerBuilder.build())
            .buildAndRegisterGlobal();

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            sdk.getSdkTracerProvider().shutdown()));

        return sdk;
    }

    /** Starts a server-kind span and returns the scope (use in try-with-resources). */
    public static Scope startSpan(String name) {
        io.opentelemetry.api.trace.Tracer t = GlobalOpenTelemetry.getTracer("gkit");
        Span span = t.spanBuilder(name).setSpanKind(SpanKind.SERVER).startSpan();
        return span.makeCurrent();
    }

    /** Records an error on the current span. */
    public static void recordError(Throwable t) {
        Span current = Span.current();
        current.recordException(t);
        current.setStatus(StatusCode.ERROR, t.getMessage());
    }

    /** Runs callable inside a span; records errors automatically. */
    public static <T> T traced(String name, Callable<T> fn) throws Exception {
        io.opentelemetry.api.trace.Tracer t = GlobalOpenTelemetry.getTracer("gkit");
        Span span = t.spanBuilder(name).startSpan();
        try (Scope scope = span.makeCurrent()) {
            T result = fn.call();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
