package com.hawa.hawa_backend.admin.dto;

import java.util.Map;

import com.hawa.hawa_backend.enums.ReportStatusEnum;

public record SystemAnalyticsResponse(
        long totalUsers,
        long totalCompanies,
        long totalReports,
        Map<ReportStatusEnum, Long> reportsByStatus,
        long totalPostsAnalyzed
) {}
