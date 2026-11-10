CREATE SCHEMA payments;

-- One row per authorisation request, approved or declined. No card number is stored here.
CREATE TABLE payments.authorizations (
                                         id                    uuid         PRIMARY KEY,
                                         network_txn_id        varchar(64)  NOT NULL,
                                         card_id               uuid,
                                         account_id            uuid,
                                         amount_minor          bigint       NOT NULL,
                                         currency              varchar(3)   NOT NULL,
                                         merchant_id           varchar(64)  NOT NULL,
                                         merchant_name         varchar(200) NOT NULL,
                                         mcc                   varchar(4)   NOT NULL,
                                         merchant_country      varchar(2)   NOT NULL,
                                         category              varchar(40)  NOT NULL,
                                         status                varchar(20)  NOT NULL,
                                         decline_reason        varchar(40),
                                         auth_code             varchar(6),
                                         cleared_amount_minor  bigint       NOT NULL,
                                         refunded_amount_minor bigint       NOT NULL,
                                         created_at            timestamptz  NOT NULL,
                                         updated_at            timestamptz  NOT NULL,
                                         version               bigint       NOT NULL,
    -- A network retry can never create a second authorisation
                                         CONSTRAINT uq_authorizations_network_txn_id UNIQUE (network_txn_id),
                                         CONSTRAINT ck_authorizations_status CHECK (status IN ('APPROVED', 'DECLINED', 'CLEARED', 'REVERSED')),
    -- An approval always has an auth code, a decline always has a reason
                                         CONSTRAINT ck_authorizations_outcome CHECK (
                                             (status = 'DECLINED' AND decline_reason IS NOT NULL AND auth_code IS NULL)
                                                 OR (status <> 'DECLINED' AND auth_code IS NOT NULL AND decline_reason IS NULL)),
                                         CONSTRAINT ck_authorizations_amounts CHECK (
                                             amount_minor > 0
                                                 AND cleared_amount_minor BETWEEN 0 AND amount_minor
                                                 AND refunded_amount_minor BETWEEN 0 AND cleared_amount_minor)
);

CREATE INDEX ix_authorizations_account ON payments.authorizations (account_id);

-- Clearings, reversals and refunds, keyed by the network's own operation ID so retries are answered, not repeated
CREATE TABLE payments.network_operations (
                                             id             uuid        PRIMARY KEY,
                                             operation_type varchar(20) NOT NULL,
                                             operation_id   varchar(64) NOT NULL,
                                             network_txn_id varchar(64) NOT NULL,
                                             amount_minor   bigint,
                                             accepted       boolean     NOT NULL,
                                             reject_reason  varchar(40),
                                             created_at     timestamptz NOT NULL,
                                             CONSTRAINT ck_network_operations_type CHECK (operation_type IN ('CLEAR', 'REVERSE', 'REFUND')),
                                             CONSTRAINT ck_network_operations_outcome CHECK (accepted = (reject_reason IS NULL)),
                                             CONSTRAINT uq_network_operations_type_id UNIQUE (operation_type, operation_id)
);