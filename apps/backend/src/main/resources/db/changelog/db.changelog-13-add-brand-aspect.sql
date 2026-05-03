-- liquibase formatted sql

-- changeset hawa:13-add-brand-aspect
-- comment: Add BRAND to the aspect enum to match the LLM service output
ALTER TYPE aspect ADD VALUE 'BRAND';

-- rollback not supported: PostgreSQL cannot remove an enum value in a single DDL
