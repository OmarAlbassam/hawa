-- ============================================================
-- V2: Add email and password columns to "user" for authentication
-- ============================================================

ALTER TABLE "user"
    ADD COLUMN email    VARCHAR(255) NOT NULL UNIQUE,
    ADD COLUMN password VARCHAR(255) NOT NULL;

CREATE UNIQUE INDEX idx_user_email ON "user"(email);
