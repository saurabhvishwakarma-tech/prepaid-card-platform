package com.cardplatform.core.ledger.api;

// A decline is a normal outcome, not an exception: an exception leaving a @Transactional method
// would roll back the caller's transaction, including its record of the declined payment.
public enum HoldResult {
    PLACED,
    INSUFFICIENT_FUNDS
}