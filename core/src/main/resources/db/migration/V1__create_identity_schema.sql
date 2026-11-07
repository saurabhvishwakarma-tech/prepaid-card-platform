CREATE SCHEMA identity;

CREATE TABLE identity.users (
                                id            uuid         PRIMARY KEY,
                                email         varchar(254) NOT NULL,
                                password_hash varchar(100) NOT NULL,
                                full_name     varchar(100) NOT NULL,
                                role          varchar(20)  NOT NULL,
                                kyc_status    varchar(20)  NOT NULL,
                                created_at    timestamptz  NOT NULL,
                                updated_at    timestamptz  NOT NULL,
                                version       bigint       NOT NULL,
                                CONSTRAINT uq_users_email UNIQUE (email),
    -- The app stores emails lower-cased; the database refuses anything else
                                CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
                                CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'SUPPORT', 'ADMIN')),
                                CONSTRAINT ck_users_kyc_status CHECK (kyc_status IN ('PENDING', 'VERIFIED'))
);