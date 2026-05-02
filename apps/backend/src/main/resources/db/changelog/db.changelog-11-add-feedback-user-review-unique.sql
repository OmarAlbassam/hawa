-- liquibase formatted sql

-- changeset hawa:11-add-feedback-user-review-unique
-- comment: Enforce one feedback per (user, review). The service performs an idempotent upsert, but without a DB-level unique constraint two concurrent submissions can race past the SELECT and insert duplicates.
ALTER TABLE feedback
    ADD CONSTRAINT feedback_user_review_unique UNIQUE (user_id, review_id);

-- rollback ALTER TABLE feedback DROP CONSTRAINT feedback_user_review_unique;
