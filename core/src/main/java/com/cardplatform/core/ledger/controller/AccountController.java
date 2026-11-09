package com.cardplatform.core.ledger.controller;

import com.cardplatform.core.ledger.dto.AccountResponse;
import com.cardplatform.core.ledger.exception.AccountNotFoundException;
import com.cardplatform.core.ledger.mapper.AccountMapper;
import com.cardplatform.core.ledger.service.LedgerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/core/accounts")
public class AccountController {

    private final LedgerService ledgerService;
    private final AccountMapper accountMapper;

    public AccountController(LedgerService ledgerService, AccountMapper accountMapper) {
        this.ledgerService = ledgerService;
        this.accountMapper = accountMapper;
    }

    @GetMapping("/me")
    public AccountResponse myAccount(@AuthenticationPrincipal Jwt jwt) {
        return ledgerService.findCustomerAccount(UUID.fromString(jwt.getSubject()))
                .map(accountMapper::toResponse)
                .orElseThrow(AccountNotFoundException::new);
    }
}