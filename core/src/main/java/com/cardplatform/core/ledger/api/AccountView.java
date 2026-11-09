package com.cardplatform.core.ledger.api;

import java.util.UUID;

public record AccountView(UUID accountId, UUID userId, String currency, long ledgerBalance,
                          long availableBalance) {
}