-- liquibase formatted sql

-- changeset hawa:2-add-auth-columns
-- comment: Add email and password columns to user for authentication

ALTER TABLE "user"
    ADD COLUMN email    VARCHAR(255) NOT NULL UNIQUE,
    ADD COLUMN password VARCHAR(255) NOT NULL;

-- rollback ALTER TABLE "user" DROP COLUMN IF EXISTS email, DROP COLUMN IF EXISTS password;
