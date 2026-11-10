package com.cardplatform.simulator.grpc;

import com.cardplatform.contracts.network.v1.AuthorizeRequest;
import com.cardplatform.contracts.network.v1.AuthorizeResponse;
import com.cardplatform.contracts.network.v1.CardNetworkServiceGrpc.CardNetworkServiceBlockingStub;
import com.cardplatform.contracts.network.v1.ClearRequest;
import com.cardplatform.contracts.network.v1.ClearResponse;
import com.cardplatform.contracts.network.v1.RefundRequest;
import com.cardplatform.contracts.network.v1.RefundResponse;
import com.cardplatform.contracts.network.v1.ReverseRequest;
import com.cardplatform.contracts.network.v1.ReverseResponse;
import com.cardplatform.simulator.config.NetworkProperties;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// Calls core with a deadline on every attempt and retries when core doesn't answer.
// Requests carry full card numbers, so only transaction IDs are logged.
@Component
public class CoreNetworkClient {

    private static final Logger log = LoggerFactory.getLogger(CoreNetworkClient.class);

    // Only "no answer" is worth retrying; a rejected or malformed request would fail the same way again
    private static final Set<Status.Code> RETRYABLE = EnumSet.of(Status.Code.DEADLINE_EXCEEDED, Status.Code.UNAVAILABLE);

    private final CardNetworkServiceBlockingStub core;
    private final NetworkProperties properties;

    public CoreNetworkClient(CardNetworkServiceBlockingStub core, NetworkProperties properties) {
        this.core = core;
        this.properties = properties;
    }

    public Attempted<AuthorizeResponse> authorize(AuthorizeRequest request) {
        return call("Authorize", request.getNetworkTxnId(), stub -> stub.authorize(request));
    }

    public Attempted<ClearResponse> clear(ClearRequest request) {
        return call("Clear", request.getNetworkTxnId(), stub -> stub.clear(request));
    }

    public Attempted<ReverseResponse> reverse(ReverseRequest request) {
        return call("Reverse", request.getNetworkTxnId(), stub -> stub.reverse(request));
    }

    public Attempted<RefundResponse> refund(RefundRequest request) {
        return call("Refund", request.getNetworkTxnId(), stub -> stub.refund(request));
    }

    // Every attempt sends the identical request, so core answers a retry from its stored result
    // and nothing is processed twice
    private <T> Attempted<T> call(String operation, String networkTxnId,
                                  Function<CardNetworkServiceBlockingStub, T> rpc) {
        for (int attempt = 1; ; attempt++) {
            try {
                // A fresh deadline per attempt: each try gets the full time budget
                CardNetworkServiceBlockingStub stub =
                        core.withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS);
                return new Attempted<>(rpc.apply(stub), attempt);
            } catch (StatusRuntimeException e) {
                Status.Code code = e.getStatus().getCode();
                if (!RETRYABLE.contains(code) || attempt >= properties.maxAttempts()) {
                    log.warn("{} failed: networkTxnId={}, attempt={}, status={}", operation, networkTxnId, attempt, code);
                    throw new NetworkCallException(operation, attempt, e);
                }
                log.info("{} retrying: networkTxnId={}, attempt={}, status={}", operation, networkTxnId, attempt, code);
                pause(properties.retryBackoff().multipliedBy(attempt));
            }
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }
}