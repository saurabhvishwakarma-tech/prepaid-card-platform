CREATE SCHEMA ledger;

CREATE TABLE ledger.accounts (
                                 id                uuid        PRIMARY KEY,
                                 owner_type        varchar(20) NOT NULL,
                                 user_id           uuid,
                                 code              varchar(50),
                                 currency          varchar(3)  NOT NULL,
                                 ledger_balance    bigint      NOT NULL,
                                 available_balance bigint      NOT NULL,
                                 created_at        timestamptz NOT NULL,
                                 updated_at        timestamptz NOT NULL,
                                 version           bigint      NOT NULL,
                                 CONSTRAINT ck_accounts_owner CHECK (
                                     (owner_type = 'CUSTOMER' AND user_id IS NOT NULL AND code IS NULL)
                                         OR (owner_type = 'SYSTEM' AND code IS NOT NULL AND user_id IS NULL)),
    -- Customer money can never go below zero, and holds can only reduce what is available
                                 CONSTRAINT ck_accounts_customer_balances CHECK (
                                     owner_type = 'SYSTEM'
                                         OR (available_balance >= 0 AND available_balance <= ledger_balance)),
                                 CONSTRAINT uq_accounts_user_currency UNIQUE (user_id, currency),
                                 CONSTRAINT uq_accounts_code UNIQUE (code)
);

CREATE TABLE ledger.journal_entries (
                                        id         uuid         PRIMARY KEY,
                                        entry_type varchar(30)  NOT NULL,
                                        reference  varchar(100) NOT NULL,
                                        created_at timestamptz  NOT NULL,
                                        CONSTRAINT ck_journal_entries_type CHECK (entry_type IN ('TOP_UP', 'CARD_CAPTURE', 'CARD_REFUND')),
    -- The same business event can only move money once
                                        CONSTRAINT uq_journal_entries_type_reference UNIQUE (entry_type, reference)
);

CREATE TABLE ledger.postings (
                                 id               uuid        PRIMARY KEY,
                                 journal_entry_id uuid        NOT NULL REFERENCES ledger.journal_entries (id),
                                 account_id       uuid        NOT NULL REFERENCES ledger.accounts (id),
                                 amount           bigint      NOT NULL,
                                 currency         varchar(3)  NOT NULL,
                                 created_at       timestamptz NOT NULL,
                                 CONSTRAINT ck_postings_amount_not_zero CHECK (amount <> 0)
);

CREATE INDEX ix_postings_account ON ledger.postings (account_id);
CREATE INDEX ix_postings_journal_entry ON ledger.postings (journal_entry_id);

CREATE TABLE ledger.holds (
                              id         uuid         PRIMARY KEY,
                              account_id uuid         NOT NULL REFERENCES ledger.accounts (id),
                              reference  varchar(100) NOT NULL,
                              amount     bigint       NOT NULL,
                              currency   varchar(3)   NOT NULL,
                              status     varchar(20)  NOT NULL,
                              created_at timestamptz  NOT NULL,
                              updated_at timestamptz  NOT NULL,
                              version    bigint       NOT NULL,
                              CONSTRAINT ck_holds_amount_positive CHECK (amount > 0),
                              CONSTRAINT ck_holds_status CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED')),
                              CONSTRAINT uq_holds_reference UNIQUE (reference)
);

CREATE INDEX ix_holds_account_status ON ledger.holds (account_id, status);

-- The platform's own accounts: the other side of every customer posting
INSERT INTO ledger.accounts (id, owner_type, code, currency, ledger_balance, available_balance,
                             created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-00000000f001', 'SYSTEM', 'TOP_UP_FUNDING', 'EUR', 0, 0, now(), now(), 0),
       ('00000000-0000-0000-0000-00000000f002', 'SYSTEM', 'MERCHANT_SETTLEMENT', 'EUR', 0, 0, now(), now(), 0);