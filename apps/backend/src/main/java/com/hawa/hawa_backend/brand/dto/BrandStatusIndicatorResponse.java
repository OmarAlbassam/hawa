package com.hawa.hawa_backend.brand.dto;

import java.math.BigDecimal;
import java.util.List;

import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.report.dto.StatusIndicatorResponse.AspectShare;
import com.hawa.hawa_backend.report.dto.StatusIndicatorResponse.EmotionShare;
import com.hawa.hawa_backend.report.dto.StatusIndicatorResponse.SentimentBreakdown;

public record BrandStatusIndicatorResponse(
        Long brandId,
        String brandName,
        long completedReportCount,
        BigDecimal averageSentiment,
        long analyzedPostCount,
        SentimentBreakdown sentimentBreakdown,
        EmotionEnum dominantEmotion,
        List<EmotionShare> topEmotions,
        List<AspectShare> topAspects
) {}
