-- liquibase formatted sql

-- changeset hawa:5-add-neutral-emotion
-- comment: Add NEUTRAL to the emotion enum to match the LLM service output
ALTER TYPE emotion ADD VALUE 'NEUTRAL';

-- rollback not supported: PostgreSQL cannot remove an enum value in a single DDL
