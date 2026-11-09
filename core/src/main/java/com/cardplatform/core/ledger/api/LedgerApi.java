package com.cardplatform.core.ledger.api;
import com.cardplatform.core.shared.money.Money;
import java.util.Optional;
import java.util.UUID;

public interface LedgerApi {

    AccountView openCustomerAccount(UUID userId);
    Optional<AccountView> findCustomerAccount(UUID userId);
    AccountView getAccount(UUID accountId);
    HoldResult placeHold(UUID accountId, String reference, Money amount);

    void captureHold(String reference, Money amount);
    void releaseHold(String reference);
    void creditTopUp(UUID accountId, String reference, Money amount);
    void creditRefund(UUID accountId, String reference, Money amount);

}