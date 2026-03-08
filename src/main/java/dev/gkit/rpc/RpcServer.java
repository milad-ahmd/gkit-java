package dev.gkit.rpc;

import io.grpc.*;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server builder with production-ready defaults.
 *
 * <pre>{@code
 * RpcServer server = RpcServer.builder()
 *     .port(50051)
 *     .addService(new MyServiceImpl())
 *     .enableReflection()
 *     .build();
 *
 * server.start();
 * server.awaitTermination();
 * }</pre>
 */
public final class RpcServer {

    private final Server server;

    private RpcServer(Server server) { this.server = server; }

    public void start() throws IOException { server.start(); }

    public void stop() throws InterruptedException {
        server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }

    public void awaitTermination() throws InterruptedException { server.awaitTermination(); }

    public int getPort() { return server.getPort(); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int port = 50051;
        private final ServerBuilder<?> sb;
        private boolean reflection = true;

        Builder() { this.sb = ServerBuilder.forPort(port); }

        public Builder port(int p) {
            this.port = p;
            return this;
        }

        public Builder addService(BindableService svc) { sb.addService(svc); return this; }

        public Builder addInterceptor(ServerInterceptor interceptor) {
            sb.intercept(interceptor); return this;
        }

        public Builder enableReflection() { this.reflection = true; return this; }
        public Builder disableReflection() { this.reflection = false; return this; }

        public RpcServer build() {
            if (reflection) sb.addService(ProtoReflectionService.newInstance());
            return new RpcServer(ServerBuilder.forPort(port)
                .addService(ProtoReflectionService.newInstance())
                .build());
        }
    }
}
