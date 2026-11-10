CREATE SCHEMA funding;

-- One row per top-up request. Only Stripe's references are stored here, never card data.
CREATE TABLE funding.top_ups (
                                 id                uuid         PRIMARY KEY,
                                 user_id           uuid         NOT NULL,
                                 account_id        uuid         NOT NULL,
                                 idempotency_key   varchar(64)  NOT NULL,
                                 amount_minor      bigint       NOT NULL,
                                 currency          varchar(3)   NOT NULL,
                                 payment_method_id varchar(100) NOT NULL,
                                 status            varchar(20)  NOT NULL,
                                 payment_intent_id varchar(100),
                                 failure_code      varchar(60),
                                 created_at        timestamptz  NOT NULL,
                                 updated_at        timestamptz  NOT NULL,
                                 version           bigint       NOT NULL,
    -- A retried request finds its first attempt instead of starting a second charge
                                 CONSTRAINT uq_top_ups_user_key UNIQUE (user_id, idempotency_key),
                                 CONSTRAINT uq_top_ups_payment_intent UNIQUE (payment_intent_id),
                                 CONSTRAINT ck_top_ups_status CHECK (status IN ('CREATED', 'PENDING', 'COMPLETED', 'FAILED')),
                                 CONSTRAINT ck_top_ups_amount CHECK (amount_minor > 0)
);

CREATE INDEX ix_top_ups_status_created ON funding.top_ups (status, created_at);

-- Stripe events already applied; Stripe retries deliveries, so the same event can arrive more than once
CREATE TABLE funding.stripe_events (
                                       event_id    varchar(100) PRIMARY KEY,
                                       event_type  varchar(100) NOT NULL,
                                       received_at timestamptz  NOT NULL
);

-- The expiry job looks for old approvals
CREATE INDEX ix_authorizations_status_created ON payments.authorizations (status, created_at);