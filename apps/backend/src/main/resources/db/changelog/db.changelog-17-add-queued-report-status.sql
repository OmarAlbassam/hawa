-- liquibase formatted sql

-- changeset hawa:17-add-queued-report-status
-- comment: Add QUEUED to report_status so reports waiting for the LLM worker are visible
ALTER TYPE report_status ADD VALUE 'QUEUED' AFTER 'PENDING';

-- rollback not supported: PostgreSQL cannot remove an enum value in a single DDL
