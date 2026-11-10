package com.cardplatform.core.payments.dto;

import com.cardplatform.core.shared.money.Money;

public record RefundCommand(String refundId, String networkTxnId, Money amount) {
}