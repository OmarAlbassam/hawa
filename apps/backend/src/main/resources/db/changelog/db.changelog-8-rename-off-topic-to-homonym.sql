-- liquibase formatted sql

-- changeset hawa:8-rename-off-topic-to-homonym
-- comment: Rename the OFF_TOPIC irrelevance reason to HOMONYM after tightening the relevance prompt; existing rows are renamed in place by ALTER TYPE.
ALTER TYPE irrelevance_reason RENAME VALUE 'OFF_TOPIC' TO 'HOMONYM';

-- rollback ALTER TYPE irrelevance_reason RENAME VALUE 'HOMONYM' TO 'OFF_TOPIC';
