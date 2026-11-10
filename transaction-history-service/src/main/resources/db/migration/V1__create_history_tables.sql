-- One row per customer-visible transaction, built from events; never the record of money
CREATE TABLE transactions (
                              id             uuid         PRIMARY KEY,
                              user_id        uuid         NOT NULL,
                              account_id     uuid         NOT NULL,
                              type           varchar(20)  NOT NULL,
    -- network_txn_id for card payments, the top-up or refund ID otherwise
                              reference      varchar(100) NOT NULL,
                              status         varchar(20)  NOT NULL,
                              amount_minor   bigint       NOT NULL,
                              currency       varchar(3)   NOT NULL,
                              merchant_name  varchar(200),
                              category       varchar(40),
                              decline_reason varchar(40),
                              occurred_at    timestamptz  NOT NULL,
                              updated_at     timestamptz  NOT NULL,
                              version        bigint       NOT NULL,
                              CONSTRAINT uq_transactions_type_reference UNIQUE (type, reference),
                              CONSTRAINT ck_transactions_type CHECK (type IN ('CARD_PAYMENT', 'TOP_UP', 'REFUND')),
                              CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'DECLINED', 'REVERSED')),
                              CONSTRAINT ck_transactions_amount CHECK (amount_minor > 0)
);

CREATE INDEX ix_transactions_user_time ON transactions (user_id, occurred_at DESC);

-- Every event already applied; a redelivered event is recognised here and skipped
CREATE TABLE processed_events (
                                  event_id     uuid        PRIMARY KEY,
                                  processed_at timestamptz NOT NULL
);