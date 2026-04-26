-- liquibase formatted sql

-- changeset hawa:9-drop-review-confidence
-- comment: Drop the review.confidence column. The LLM service does not produce a confidence value, and the backend has been hardcoding it to 1.0 — the field carries no signal.
ALTER TABLE review DROP COLUMN confidence;

-- rollback ALTER TABLE review ADD COLUMN confidence NUMERIC NOT NULL DEFAULT 1.0;
