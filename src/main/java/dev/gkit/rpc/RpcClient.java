package dev.gkit.rpc;

import io.grpc.*;

/**
 * gRPC client factory with sensible defaults.
 *
 * <pre>{@code
 * ManagedChannel channel = RpcClient.dial("localhost:50051");
 * MyServiceGrpc.MyServiceBlockingStub stub = MyServiceGrpc.newBlockingStub(channel);
 * }</pre>
 */
public final class RpcClient {

    private RpcClient() {}

    /** Opens an insecure channel to the given target. */
    public static ManagedChannel dial(String target) {
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }

    /** Opens a channel with custom options. */
    public static ManagedChannel dial(String target, ChannelCredentials credentials) {
        return Grpc.newChannelBuilder(target, credentials).build();
    }

    /** Opens an insecure channel with interceptors. */
    public static ManagedChannel dial(String target, ClientInterceptor... interceptors) {
        return ManagedChannelBuilder.forTarget(target)
            .usePlaintext()
            .intercept(interceptors)
            .build();
    }
}
