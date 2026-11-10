package com.cardplatform.core.ledger.controller;

import com.cardplatform.core.ledger.api.AccountView;
import com.cardplatform.core.ledger.dto.AccountResponse;
import com.cardplatform.core.ledger.dto.DevTopUpRequest;
import com.cardplatform.core.ledger.exception.AccountNotFoundException;
import com.cardplatform.core.ledger.mapper.AccountMapper;
import com.cardplatform.core.ledger.service.LedgerService;
import com.cardplatform.core.shared.money.Money;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Development only: puts test money on the caller's own account until Stripe top-ups arrive (Day 10).
// The bean exists only when core runs with the "dev" profile, so the endpoint is absent everywhere else.
@Profile("dev")
@RestController
@RequestMapping("/api/core/dev")
public class DevTopUpController {

    private final LedgerService ledgerService;
    private final AccountMapper accountMapper;

    public DevTopUpController(LedgerService ledgerService, AccountMapper accountMapper) {
        this.ledgerService = ledgerService;
        this.accountMapper = accountMapper;
    }

    @PostMapping("/top-ups")
    public AccountResponse topUp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DevTopUpRequest request) {
        AccountView account = ledgerService.findCustomerAccount(UUID.fromString(jwt.getSubject()))
                .orElseThrow(AccountNotFoundException::new);
        ledgerService.creditTopUp(account.accountId(), "dev-" + UUID.randomUUID(), Money.eur(request.amountMinor()));
        return accountMapper.toResponse(ledgerService.getAccount(account.accountId()));
    }
}