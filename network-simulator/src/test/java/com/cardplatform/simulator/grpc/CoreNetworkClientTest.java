package com.cardplatform.simulator.grpc;

import com.cardplatform.contracts.network.v1.AuthorizationDecision;
import com.cardplatform.contracts.network.v1.AuthorizeRequest;
import com.cardplatform.contracts.network.v1.AuthorizeResponse;
import com.cardplatform.contracts.network.v1.CardNetworkServiceGrpc;
import com.cardplatform.simulator.config.NetworkProperties;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreNetworkClientTest {

    private final List<AuthorizeRequest> received = new CopyOnWriteArrayList<>();
    private final Queue<Status> failuresBeforeSuccess = new ConcurrentLinkedQueue<>();
    private volatile long firstCallDelayMs;

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void startFakeCore() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).addService(new FakeCore()).build().start();
        channel = InProcessChannelBuilder.forName(name).build();
    }

    @AfterEach
    void stopFakeCore() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void retriesUnavailableWithTheSameRequestUntilItSucceeds() {
        failuresBeforeSuccess.add(Status.UNAVAILABLE);
        failuresBeforeSuccess.add(Status.UNAVAILABLE);

        Attempted<AuthorizeResponse> result = client(Duration.ofSeconds(1)).authorize(request());

        assertThat(result.attempts()).isEqualTo(3);
        assertThat(result.response().getAuthCode()).isEqualTo("ABC123");
        // Every attempt carried the same transaction ID, which is what makes retries safe in core
        assertThat(received).hasSize(3);
        assertThat(received.stream().map(AuthorizeRequest::getNetworkTxnId).distinct()).hasSize(1);
    }

    @Test
    void retriesAfterADeadlineIsMissed() {
        firstCallDelayMs = 500;

        Attempted<AuthorizeResponse> result = client(Duration.ofMillis(100)).authorize(request());

        assertThat(result.attempts()).isEqualTo(2);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        for (int i = 0; i < 5; i++) {
            failuresBeforeSuccess.add(Status.UNAVAILABLE);
        }

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).authorize(request()))
                .isInstanceOfSatisfying(NetworkCallException.class, e -> {
                    assertThat(e.getAttempts()).isEqualTo(3);
                    assertThat(e.isUnanswered()).isTrue();
                });
        assertThat(received).hasSize(3);
    }

    @Test
    void doesNotRetryARequestCoreRejected() {
        failuresBeforeSuccess.add(Status.INVALID_ARGUMENT);

        assertThatThrownBy(() -> client(Duration.ofSeconds(1)).authorize(request()))
                .isInstanceOfSatisfying(NetworkCallException.class, e -> {
                    assertThat(e.getAttempts()).isEqualTo(1);
                    assertThat(e.isUnanswered()).isFalse();
                });
        assertThat(received).hasSize(1);
    }

    private CoreNetworkClient client(Duration deadline) {
        NetworkProperties properties = new NetworkProperties("test-network", "x".repeat(32), deadline, 3,
                Duration.ofMillis(10));
        return new CoreNetworkClient(CardNetworkServiceGrpc.newBlockingStub(channel), properties);
    }

    private static AuthorizeRequest request() {
        return AuthorizeRequest.newBuilder().setNetworkTxnId("ntx-" + UUID.randomUUID()).build();
    }

    private class FakeCore extends CardNetworkServiceGrpc.CardNetworkServiceImplBase {

        @Override
        public void authorize(AuthorizeRequest request, StreamObserver<AuthorizeResponse> responseObserver) {
            received.add(request);
            if (received.size() == 1 && firstCallDelayMs > 0) {
                sleep(firstCallDelayMs);
            }
            // The client may have given up while we were slow; answering a cancelled call would only log noise
            if (((ServerCallStreamObserver<AuthorizeResponse>) responseObserver).isCancelled()) {
                return;
            }
            Status failure = failuresBeforeSuccess.poll();
            if (failure != null) {
                responseObserver.onError(failure.asRuntimeException());
                return;
            }
            responseObserver.onNext(AuthorizeResponse.newBuilder()
                    .setNetworkTxnId(request.getNetworkTxnId())
                    .setDecision(AuthorizationDecision.AUTHORIZATION_DECISION_APPROVED)
                    .setAuthCode("ABC123")
                    .build());
            responseObserver.onCompleted();
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}