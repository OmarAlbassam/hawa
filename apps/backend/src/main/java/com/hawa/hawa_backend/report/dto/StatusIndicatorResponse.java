package com.hawa.hawa_backend.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;

public record StatusIndicatorResponse(
        Long reportId,
        BigDecimal averageSentiment,
        long analyzedPostCount,
        SentimentBreakdown sentimentBreakdown,
        EmotionEnum dominantEmotion,
        List<EmotionShare> topEmotions,
        List<AspectShare> topAspects,
        String summary
) {

    public record SentimentBreakdown(
            SentimentBucket negative,
            SentimentBucket neutral,
            SentimentBucket positive
    ) {}

    public record SentimentBucket(long count, BigDecimal percentage) {}

    public record EmotionShare(EmotionEnum emotion, long count, BigDecimal percentage) {}

    public record AspectShare(AspectEnum aspect, long count, BigDecimal percentage) {}

    public static SentimentBreakdown breakdownOf(long negative, long neutral, long positive, long total) {
        return new SentimentBreakdown(
                bucket(negative, total),
                bucket(neutral, total),
                bucket(positive, total));
    }

    public static SentimentBucket bucket(long count, long total) {
        return new SentimentBucket(count, share(count, total));
    }

    public static BigDecimal share(long count, long total) {
        if (total <= 0) return BigDecimal.ZERO.setScale(4);
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    public static List<EmotionShare> topEmotions(Map<EmotionEnum, Long> counts, int limit) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<EmotionEnum, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(e -> new EmotionShare(e.getKey(), e.getValue(), share(e.getValue(), total)))
                .toList();
    }

    public static List<AspectShare> topAspects(Map<AspectEnum, Long> counts, long total, int limit) {
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<AspectEnum, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(e -> new AspectShare(e.getKey(), e.getValue(), share(e.getValue(), total)))
                .toList();
    }
}
