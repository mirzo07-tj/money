CREATE TABLE transfers (
                           id BIGSERIAL PRIMARY KEY,
                           from_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
                           to_account_id BIGINT NOT NULL REFERENCES bank_accounts(id),
                           amount NUMERIC(19, 4) NOT NULL,
                           currency VARCHAR(10) NOT NULL,
                           description VARCHAR(255),
                           created_at TIMESTAMP NOT NULL
);