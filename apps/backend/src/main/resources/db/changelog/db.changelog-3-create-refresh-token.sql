-- liquibase formatted sql

-- changeset hawa:3-create-refresh-token
-- comment: Create refresh_token table for JWT refresh token storage

CREATE TABLE refresh_token (
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    token            VARCHAR(255) NOT NULL UNIQUE,
    expires_at       TIMESTAMP NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_token ON refresh_token(token);

-- rollback DROP TABLE IF EXISTS refresh_token CASCADE;
