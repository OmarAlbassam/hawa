package com.hawa.hawa_backend.analysis.dto;

import java.time.LocalDateTime;

import com.hawa.hawa_backend.enums.ReportStatusEnum;

public record ReportStatusResponse(
        Long reportId,
        ReportStatusEnum status,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {}
