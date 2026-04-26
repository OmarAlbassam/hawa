-- liquibase formatted sql

-- changeset hawa:9-add-post-created-at
-- comment: Track when each post was ingested so View Posts can filter by date range
ALTER TABLE post
    ADD COLUMN created_at timestamp NOT NULL DEFAULT now();

CREATE INDEX idx_post_report_created_at ON post (report_id, created_at);

-- rollback DROP INDEX idx_post_report_created_at;
-- rollback ALTER TABLE post DROP COLUMN created_at;
