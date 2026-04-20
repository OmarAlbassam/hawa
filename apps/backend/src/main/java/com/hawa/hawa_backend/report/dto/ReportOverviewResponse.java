package com.hawa.hawa_backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;

public record ReportOverviewResponse(
        Long reportId,
        String brandName,
        ReportStatusEnum status,
        DataSourceEnum dataSource,
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        String summary,
        Integer score,
        long totalPosts,
        BigDecimal averageSentiment,
        BigDecimal averageConfidence,
        Map<EmotionEnum, Long> emotionDistribution,
        Map<AspectEnum, Long> aspectDistribution
) {}
