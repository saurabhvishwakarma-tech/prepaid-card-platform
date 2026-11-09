package com.cardplatform.core.ledger.mapper;

import com.cardplatform.core.ledger.api.AccountView;
import com.cardplatform.core.ledger.dto.AccountResponse;
import com.cardplatform.core.ledger.model.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountView toView(Account account) {
        return new AccountView(account.getId(), account.getUserId(), account.getCurrency(),
                account.getLedgerBalance(), account.getAvailableBalance());
    }

    public AccountResponse toResponse(AccountView view) {
        return new AccountResponse(view.accountId(), view.currency(), view.ledgerBalance(),
                view.availableBalance());
    }
}
