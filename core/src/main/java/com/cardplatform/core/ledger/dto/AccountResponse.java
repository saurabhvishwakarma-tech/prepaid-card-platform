package com.cardplatform.core.ledger.dto;

import java.util.UUID;

// Balances in minor units (cents), so clients never have to parse decimals
public record AccountResponse(UUID accountId, String currency, long ledgerBalanceMinor,
                              long availableBalanceMinor) {
}