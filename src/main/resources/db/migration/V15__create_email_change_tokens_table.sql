CREATE TABLE email_change_tokens (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                     new_email VARCHAR(255) NOT NULL,
                                     token_hash VARCHAR(64) NOT NULL,
                                     expires_at TIMESTAMP NOT NULL,
                                     used BOOLEAN NOT NULL DEFAULT FALSE,
                                     created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_change_token_hash ON email_change_tokens(token_hash);