package com.hawa.hawa_backend.report.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;

public record ReportResponse(
        Long reportId,
        String brandName,
        ReportStatusEnum status,
        DataSourceEnum dataSource,
        Integer score,
        String summary,
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {}
