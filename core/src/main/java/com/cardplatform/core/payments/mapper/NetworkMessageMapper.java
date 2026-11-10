package com.cardplatform.core.payments.mapper;

import com.cardplatform.contracts.common.v1.DeclineReason;
import com.cardplatform.contracts.common.v1.Merchant;
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
import com.cardplatform.core.payments.dto.AuthorizationOutcome;
import com.cardplatform.core.payments.dto.AuthorizeCommand;
import com.cardplatform.core.payments.dto.ClearCommand;
import com.cardplatform.core.payments.dto.OperationOutcome;
import com.cardplatform.core.payments.dto.RefundCommand;
import com.cardplatform.core.payments.dto.ReverseCommand;
import com.cardplatform.core.payments.model.OperationRejectReason;
import com.cardplatform.core.payments.model.PaymentDeclineReason;
import com.cardplatform.core.shared.money.Money;
import org.springframework.stereotype.Component;

// Translates between the Protobuf contract and the payments module, so generated classes stop at the edge
@Component
public class NetworkMessageMapper {

    public AuthorizeCommand toCommand(AuthorizeRequest request) {
        Merchant merchant = request.getMerchant();
        return new AuthorizeCommand(request.getNetworkTxnId(), request.getPan(), request.getExpiryMonth(),
                request.getExpiryYear(), toMoney(request.getAmount()), merchant.getMerchantId(),
                merchant.getName(), merchant.getMcc(), merchant.getCountry());
    }

    public ClearCommand toCommand(ClearRequest request) {
        return new ClearCommand(request.getClearingId(), request.getNetworkTxnId(), toMoney(request.getAmount()));
    }

    public ReverseCommand toCommand(ReverseRequest request) {
        return new ReverseCommand(request.getReversalId(), request.getNetworkTxnId());
    }

    public RefundCommand toCommand(RefundRequest request) {
        return new RefundCommand(request.getRefundId(), request.getNetworkTxnId(), toMoney(request.getAmount()));
    }

    public AuthorizeResponse toResponse(String networkTxnId, AuthorizationOutcome outcome) {
        AuthorizeResponse.Builder response = AuthorizeResponse.newBuilder().setNetworkTxnId(networkTxnId);
        if (outcome.approved()) {
            return response.setDecision(AuthorizationDecision.AUTHORIZATION_DECISION_APPROVED)
                    .setAuthCode(outcome.authCode())
                    .build();
        }
        return response.setDecision(AuthorizationDecision.AUTHORIZATION_DECISION_DECLINED)
                .setDeclineReason(toProto(outcome.declineReason()))
                .build();
    }

    public ClearResponse toClearResponse(String clearingId, OperationOutcome outcome) {
        return ClearResponse.newBuilder()
                .setClearingId(clearingId)
                .setResult(toResult(outcome))
                .setRejectReason(toProto(outcome))
                .build();
    }

    public ReverseResponse toReverseResponse(String reversalId, OperationOutcome outcome) {
        return ReverseResponse.newBuilder()
                .setReversalId(reversalId)
                .setResult(toResult(outcome))
                .setRejectReason(toProto(outcome))
                .build();
    }

    public RefundResponse toRefundResponse(String refundId, OperationOutcome outcome) {
        return RefundResponse.newBuilder()
                .setRefundId(refundId)
                .setResult(toResult(outcome))
                .setRejectReason(toProto(outcome))
                .build();
    }

    // An unset currency arrives as "", which Money rejects, so the call fails as INVALID_ARGUMENT
    private static Money toMoney(com.cardplatform.contracts.common.v1.Money amount) {
        return new Money(amount.getAmountMinor(), amount.getCurrency());
    }

    private static ProcessingResult toResult(OperationOutcome outcome) {
        return outcome.accepted()
                ? ProcessingResult.PROCESSING_RESULT_ACCEPTED
                : ProcessingResult.PROCESSING_RESULT_REJECTED;
    }

    public static DeclineReason toProto(PaymentDeclineReason reason) {
        return switch (reason) {
            case INSUFFICIENT_FUNDS -> DeclineReason.DECLINE_REASON_INSUFFICIENT_FUNDS;
            case CARD_NOT_FOUND -> DeclineReason.DECLINE_REASON_CARD_NOT_FOUND;
            case CARD_NOT_ACTIVE -> DeclineReason.DECLINE_REASON_CARD_NOT_ACTIVE;
            case CARD_EXPIRED -> DeclineReason.DECLINE_REASON_CARD_EXPIRED;
            case TRANSACTION_LIMIT_EXCEEDED -> DeclineReason.DECLINE_REASON_TRANSACTION_LIMIT_EXCEEDED;
            case MERCHANT_CATEGORY_BLOCKED -> DeclineReason.DECLINE_REASON_MERCHANT_CATEGORY_BLOCKED;
            case CURRENCY_NOT_SUPPORTED -> DeclineReason.DECLINE_REASON_CURRENCY_NOT_SUPPORTED;
        };
    }

    private static RejectReason toProto(OperationOutcome outcome) {
        if (outcome.accepted()) {
            return RejectReason.REJECT_REASON_UNSPECIFIED;
        }
        OperationRejectReason reason = outcome.rejectReason();
        return switch (reason) {
            case AUTHORIZATION_NOT_FOUND -> RejectReason.REJECT_REASON_AUTHORIZATION_NOT_FOUND;
            case INVALID_STATE -> RejectReason.REJECT_REASON_INVALID_STATE;
            case AMOUNT_EXCEEDS_ALLOWED -> RejectReason.REJECT_REASON_AMOUNT_EXCEEDS_ALLOWED;
            case CURRENCY_MISMATCH -> RejectReason.REJECT_REASON_CURRENCY_MISMATCH;
        };
    }
}