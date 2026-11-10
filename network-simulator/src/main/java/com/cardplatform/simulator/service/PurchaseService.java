package com.cardplatform.simulator.service;

import com.cardplatform.contracts.common.v1.Money;
import com.cardplatform.contracts.network.v1.AuthorizationDecision;
import com.cardplatform.contracts.network.v1.AuthorizeRequest;
import com.cardplatform.contracts.network.v1.AuthorizeResponse;
import com.cardplatform.contracts.network.v1.ClearRequest;
import com.cardplatform.contracts.network.v1.ClearResponse;
import com.cardplatform.contracts.network.v1.ProcessingResult;
import com.cardplatform.contracts.network.v1.RefundRequest;
import com.cardplatform.contracts.network.v1.RefundResponse;
import com.cardplatform.contracts.network.v1.RejectReason;
import com.cardplatform.contracts.network.v1.ReverseRequest;
import com.cardplatform.contracts.network.v1.ReverseResponse;
import com.cardplatform.simulator.dto.OperationResponse;
import com.cardplatform.simulator.dto.PurchaseRequest;
import com.cardplatform.simulator.dto.PurchaseResponse;
import com.cardplatform.simulator.grpc.Attempted;
import com.cardplatform.simulator.grpc.CoreNetworkClient;
import com.cardplatform.simulator.grpc.NetworkCallException;
import com.cardplatform.simulator.model.Merchant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final CoreNetworkClient core;
    private final MerchantCatalogue merchants;

    public PurchaseService(CoreNetworkClient core, MerchantCatalogue merchants) {
        this.core = core;
        this.merchants = merchants;
    }

    public PurchaseResponse purchase(PurchaseRequest request) {
        Merchant merchant = merchants.get(request.merchantId());
        String networkTxnId = "ntx-" + UUID.randomUUID();
        AuthorizeRequest authorize = AuthorizeRequest.newBuilder()
                .setNetworkTxnId(networkTxnId)
                .setPan(request.pan())
                .setExpiryMonth(request.expiryMonth())
                .setExpiryYear(request.expiryYear())
                .setAmount(eur(request.amountMinor()))
                .setMerchant(com.cardplatform.contracts.common.v1.Merchant.newBuilder()
                        .setMerchantId(merchant.id())
                        .setName(merchant.name())
                        .setMcc(merchant.mcc())
                        .setCountry(merchant.country()))
                .build();

        long start = System.nanoTime();
        try {
            Attempted<AuthorizeResponse> result = core.authorize(authorize);
            AuthorizeResponse response = result.response();
            boolean approved = response.getDecision() == AuthorizationDecision.AUTHORIZATION_DECISION_APPROVED;
            PurchaseResponse purchase = new PurchaseResponse(networkTxnId, approved ? "APPROVED" : "DECLINED",
                    approved ? response.getAuthCode() : null,
                    approved ? null : withoutPrefix(response.getDeclineReason().name(), "DECLINE_REASON_"),
                    result.attempts(), elapsedMillis(start));
            log.info("Purchase {}: networkTxnId={}, merchant={}, amountMinor={}, attempts={}, latencyMs={}",
                    purchase.decision(), networkTxnId, merchant.id(), request.amountMinor(),
                    purchase.attempts(), purchase.latencyMs());
            return purchase;
        } catch (NetworkCallException e) {
            if (!e.isUnanswered()) {
                throw e;
            }
            // Core may have approved without us hearing back: a timeout reversal releases any hold it placed
            sendTimeoutReversal(networkTxnId);
            return new PurchaseResponse(networkTxnId, "TIMED_OUT", null, null, e.getAttempts(), elapsedMillis(start));
        }
    }

    public OperationResponse clear(String networkTxnId, long amountMinor) {
        String clearingId = "clr-" + UUID.randomUUID();
        Attempted<ClearResponse> result = core.clear(ClearRequest.newBuilder()
                .setClearingId(clearingId)
                .setNetworkTxnId(networkTxnId)
                .setAmount(eur(amountMinor))
                .build());
        return toResponse(networkTxnId, clearingId, result.response().getResult(),
                result.response().getRejectReason(), result.attempts());
    }

    public OperationResponse reverse(String networkTxnId) {
        String reversalId = "rev-" + UUID.randomUUID();
        Attempted<ReverseResponse> result = core.reverse(ReverseRequest.newBuilder()
                .setReversalId(reversalId)
                .setNetworkTxnId(networkTxnId)
                .build());
        return toResponse(networkTxnId, reversalId, result.response().getResult(),
                result.response().getRejectReason(), result.attempts());
    }

    public OperationResponse refund(String networkTxnId, long amountMinor) {
        String refundId = "ref-" + UUID.randomUUID();
        Attempted<RefundResponse> result = core.refund(RefundRequest.newBuilder()
                .setRefundId(refundId)
                .setNetworkTxnId(networkTxnId)
                .setAmount(eur(amountMinor))
                .build());
        return toResponse(networkTxnId, refundId, result.response().getResult(),
                result.response().getRejectReason(), result.attempts());
    }

    private void sendTimeoutReversal(String networkTxnId) {
        try {
            OperationResponse reversal = reverse(networkTxnId);
            log.info("Timeout reversal sent: networkTxnId={}, result={}, rejectReason={}",
                    networkTxnId, reversal.result(), reversal.rejectReason());
        } catch (NetworkCallException e) {
            // Core is unreachable too; a real network would keep retrying the reversal later
            log.warn("Timeout reversal failed: networkTxnId={}, status={}", networkTxnId, e.getStatusCode());
        }
    }

    private static OperationResponse toResponse(String networkTxnId, String operationId, ProcessingResult result,
                                                RejectReason rejectReason, int attempts) {
        boolean accepted = result == ProcessingResult.PROCESSING_RESULT_ACCEPTED;
        return new OperationResponse(networkTxnId, operationId, accepted ? "ACCEPTED" : "REJECTED",
                accepted ? null : withoutPrefix(rejectReason.name(), "REJECT_REASON_"), attempts);
    }

    private static Money eur(long amountMinor) {
        return Money.newBuilder().setAmountMinor(amountMinor).setCurrency("EUR").build();
    }

    // Protobuf enum names carry their type as a prefix; responses read better without it
    private static String withoutPrefix(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}