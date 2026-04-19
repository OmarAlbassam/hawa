package com.hawa.hawa_backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;

public record DashboardResponse(
        List<BrandSummary> brands,
        List<RecentReport> recentReports,
        DashboardStats stats
) {

    public record BrandSummary(
            Long brandId,
            String brandName,
            String industry,
            BigDecimal statusIndicator
    ) {}

    public record RecentReport(
            Long reportId,
            String brandName,
            ReportStatusEnum status,
            DataSourceEnum dataSource,
            Integer score,
            LocalDateTime createdAt,
            LocalDateTime finishedAt
    ) {}

    public record DashboardStats(
            long totalReports,
            long totalPostsAnalyzed,
            long totalBrands
    ) {}
}
