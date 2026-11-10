CREATE SCHEMA card;

CREATE TABLE card.cards (
                            id                          uuid        PRIMARY KEY,
                            user_id                     uuid        NOT NULL,
                            account_id                  uuid        NOT NULL,
    -- AES-GCM: 12-byte IV, then ciphertext and tag. The plain card number is never stored
                            pan_ciphertext              bytea       NOT NULL,
    -- HMAC of the card number, so a card can be found by its number without decrypting
                            pan_hash                    varchar(64) NOT NULL,
                            last_four                   varchar(4)  NOT NULL,
                            expiry_month                integer     NOT NULL,
                            expiry_year                 integer     NOT NULL,
                            status                      varchar(20) NOT NULL,
                            per_transaction_limit_minor bigint      NOT NULL,
                            created_at                  timestamptz NOT NULL,
                            updated_at                  timestamptz NOT NULL,
                            version                     bigint      NOT NULL,
                            CONSTRAINT uq_cards_pan_hash UNIQUE (pan_hash),
                            CONSTRAINT ck_cards_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CANCELLED')),
                            CONSTRAINT ck_cards_expiry_month CHECK (expiry_month BETWEEN 1 AND 12),
                            CONSTRAINT ck_cards_limit_positive CHECK (per_transaction_limit_minor > 0)
);

-- One card per user that isn't cancelled, enforced by the database and not just the service
CREATE UNIQUE INDEX uq_cards_one_current_per_user ON card.cards (user_id) WHERE status <> 'CANCELLED';

CREATE TABLE card.card_blocked_categories (
                                              card_id  uuid        NOT NULL REFERENCES card.cards (id),
                                              category varchar(40) NOT NULL,
                                              PRIMARY KEY (card_id, category)
);