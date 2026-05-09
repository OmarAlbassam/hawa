package com.hawa.hawa_backend.enums;

public enum ReportStatusEnum {
    /** Deprecated — kept for legacy DB rows. New reports use QUEUED. */
    PENDING,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}
