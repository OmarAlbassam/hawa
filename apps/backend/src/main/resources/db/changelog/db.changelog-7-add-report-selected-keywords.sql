-- liquibase formatted sql

-- changeset hawa:7-add-report-selected-keywords
-- comment: Snapshot the keyword strings selected for each analysis run so collection and LLM only see the chosen subset
ALTER TABLE report
    ADD COLUMN selected_keywords jsonb NOT NULL DEFAULT '[]'::jsonb;

-- rollback ALTER TABLE report DROP COLUMN selected_keywords;
