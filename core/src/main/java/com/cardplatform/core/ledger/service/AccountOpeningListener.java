package com.cardplatform.core.ledger.service;

import com.cardplatform.core.identity.api.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class AccountOpeningListener {

    private final LedgerService ledgerService;

    AccountOpeningListener(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // Synchronous, inside the registration transaction: a user and their account exist together or not at all
    @EventListener
    void onUserRegistered(UserRegisteredEvent event) {
        ledgerService.openCustomerAccount(event.userId());
    }
}