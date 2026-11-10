package com.cardplatform.core.payments.dto;

import com.cardplatform.core.shared.money.Money;

public record ClearCommand(String clearingId, String networkTxnId, Money amount) {
}