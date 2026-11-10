package com.cardplatform.core.payments.grpc;

import com.cardplatform.contracts.network.v1.AuthorizeRequest;
import com.cardplatform.contracts.network.v1.AuthorizeResponse;
import com.cardplatform.contracts.network.v1.CardNetworkServiceGrpc;
import com.cardplatform.contracts.network.v1.ClearRequest;
import com.cardplatform.contracts.network.v1.ClearResponse;
import com.cardplatform.contracts.network.v1.RefundRequest;
import com.cardplatform.contracts.network.v1.RefundResponse;
import com.cardplatform.contracts.network.v1.ReverseRequest;
import com.cardplatform.contracts.network.v1.ReverseResponse;
import com.cardplatform.core.payments.mapper.NetworkMessageMapper;
import com.cardplatform.core.payments.service.PaymentService;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

// The card network's entry point into core. Requests carry full card numbers, so they are never logged.
@GrpcService
public class CardNetworkGrpcService extends CardNetworkServiceGrpc.CardNetworkServiceImplBase {

    private final PaymentService payments;
    private final NetworkMessageMapper mapper;

    public CardNetworkGrpcService(PaymentService payments, NetworkMessageMapper mapper) {
        this.payments = payments;
        this.mapper = mapper;
    }

    @Override
    public void authorize(AuthorizeRequest request, StreamObserver<AuthorizeResponse> responseObserver) {
        respond(responseObserver, mapper.toResponse(request.getNetworkTxnId(),
                payments.authorize(mapper.toCommand(request))));
    }

    @Override
    public void clear(ClearRequest request, StreamObserver<ClearResponse> responseObserver) {
        respond(responseObserver, mapper.toClearResponse(request.getClearingId(),
                payments.clear(mapper.toCommand(request))));
    }

    @Override
    public void reverse(ReverseRequest request, StreamObserver<ReverseResponse> responseObserver) {
        respond(responseObserver, mapper.toReverseResponse(request.getReversalId(),
                payments.reverse(mapper.toCommand(request))));
    }

    @Override
    public void refund(RefundRequest request, StreamObserver<RefundResponse> responseObserver) {
        respond(responseObserver, mapper.toRefundResponse(request.getRefundId(),
                payments.refund(mapper.toCommand(request))));
    }

    private static <T> void respond(StreamObserver<T> observer, T response) {
        observer.onNext(response);
        observer.onCompleted();
    }
}