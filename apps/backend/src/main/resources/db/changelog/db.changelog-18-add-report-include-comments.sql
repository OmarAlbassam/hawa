-- liquibase formatted sql

-- changeset hawa:18-add-report-include-comments
-- comment: Per-analysis toggle: when true, the Reddit collector also pulls top-level comments from each submission
ALTER TABLE report
    ADD COLUMN include_comments boolean NOT NULL DEFAULT false;

-- rollback ALTER TABLE report DROP COLUMN include_comments;
