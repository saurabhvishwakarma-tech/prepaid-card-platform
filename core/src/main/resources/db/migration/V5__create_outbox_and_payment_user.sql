CREATE SCHEMA messaging;

-- Events waiting to be published. Written in the same transaction as the change they describe,
-- so a committed change always has its event and a rolled-back change never does.
CREATE TABLE messaging.outbox (
                                  id           uuid         PRIMARY KEY,
    -- Insert order; the relay publishes in this order so one account's events stay in sequence
                                  seq          bigint       GENERATED ALWAYS AS IDENTITY,
                                  topic        varchar(100) NOT NULL,
                                  message_key  varchar(100) NOT NULL,
                                  event_type   varchar(60)  NOT NULL,
                                  payload      bytea        NOT NULL,
                                  created_at   timestamptz  NOT NULL,
                                  published_at timestamptz,
                                  version      bigint       NOT NULL
);

CREATE INDEX ix_outbox_unpublished ON messaging.outbox (seq) WHERE published_at IS NULL;

-- The customer behind a payment, so downstream services can show each customer their own transactions
ALTER TABLE payments.authorizations ADD COLUMN user_id uuid;