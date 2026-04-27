package com.hawa.hawa_backend.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;

public record AspectBreakdownResponse(
        Long reportId,
        List<AspectBreakdownItem> aspects
) {
    public record AspectBreakdownItem(
            AspectEnum aspect,
            long postCount,
            BigDecimal averageSentiment,
            Map<EmotionEnum, Long> emotionDistribution
    ) {}
}
