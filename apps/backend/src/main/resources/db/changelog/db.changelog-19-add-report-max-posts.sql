-- liquibase formatted sql

-- changeset hawa:19-add-report-max-posts
-- comment: Per-analysis user-chosen post limit (range 1..500) — replaces the REDDIT_MAX_POSTS env var
ALTER TABLE report
    ADD COLUMN max_posts integer NOT NULL DEFAULT 50;

-- rollback ALTER TABLE report DROP COLUMN max_posts;
