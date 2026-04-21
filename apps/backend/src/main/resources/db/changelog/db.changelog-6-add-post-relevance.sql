-- liquibase formatted sql

-- changeset hawa:6-add-post-relevance
-- comment: Track per-post relevance so off-topic posts are stored but excluded from report aggregations
CREATE TYPE relevance_status AS ENUM ('RELEVANT', 'IRRELEVANT');
CREATE TYPE irrelevance_reason AS ENUM ('OFF_TOPIC', 'SPAM', 'EMPTY', 'WRONG_LANGUAGE', 'OTHER');

ALTER TABLE post
    ADD COLUMN relevance_status relevance_status NOT NULL DEFAULT 'RELEVANT',
    ADD COLUMN irrelevance_reason irrelevance_reason;

CREATE INDEX idx_post_report_relevance ON post (report_id, relevance_status);

-- rollback DROP INDEX idx_post_report_relevance;
-- rollback ALTER TABLE post DROP COLUMN irrelevance_reason;
-- rollback ALTER TABLE post DROP COLUMN relevance_status;
-- rollback DROP TYPE irrelevance_reason;
-- rollback DROP TYPE relevance_status;
