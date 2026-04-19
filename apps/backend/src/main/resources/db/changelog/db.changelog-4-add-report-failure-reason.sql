-- liquibase formatted sql

-- changeset hawa:4-add-report-failure-reason
-- comment: Store failure reason on report for surfaced LLM/collector errors

ALTER TABLE report ADD COLUMN failure_reason VARCHAR(500);

-- rollback ALTER TABLE report DROP COLUMN failure_reason;
