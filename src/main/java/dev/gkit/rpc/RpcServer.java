package dev.gkit.rpc;

import io.grpc.*;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
        private final List<BindableService> services = new ArrayList<>();
        private final List<ServerInterceptor> interceptors = new ArrayList<>();
        private boolean reflection = true;

        public Builder port(int p) {
            this.port = p;
            return this;
        }

        public Builder addService(BindableService svc) { services.add(svc); return this; }

        public Builder addInterceptor(ServerInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        public Builder enableReflection() { this.reflection = true; return this; }
        public Builder disableReflection() { this.reflection = false; return this; }

        public RpcServer build() {
            ServerBuilder<?> sb = ServerBuilder.forPort(port);
            for (BindableService svc : services) {
                sb.addService(svc);
            }
            for (ServerInterceptor interceptor : interceptors) {
                sb.intercept(interceptor);
            }
            if (reflection) {
                sb.addService(ProtoReflectionService.newInstance());
            }
            return new RpcServer(sb.build());
        }
    }
}
