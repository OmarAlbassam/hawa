package com.hawa.hawa_backend.statusindicator.dto;

import java.math.BigDecimal;
import java.util.List;

import com.hawa.hawa_backend.enums.EmotionEnum;

public record StatusIndicatorResponse(
        BigDecimal averageSentiment,
        SentimentCategory sentimentCategory,
        SentimentBreakdown sentimentBreakdown,
        EmotionEnum dominantEmotion,
        List<EmotionShare> topEmotions,
        BigDecimal emotionDiversity,
        String summary,
        long totalAnalyzedPosts
) {
    public enum SentimentCategory { NEGATIVE, NEUTRAL, POSITIVE }

    public record SentimentBreakdown(long negative, long neutral, long positive) {}

    public record EmotionShare(EmotionEnum emotion, long count, BigDecimal percentage) {}
}
