ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE email_verification_tokens (
                                           id BIGSERIAL PRIMARY KEY,
                                           user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           token_hash VARCHAR(64) NOT NULL,
                                           expires_at TIMESTAMP NOT NULL,
                                           used BOOLEAN NOT NULL DEFAULT FALSE,
                                           created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_verification_token_hash ON email_verification_tokens(token_hash);
UPDATE users SET email_verified = TRUE WHERE created_at < NOW();