package com.hawa.hawa_backend.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;

public record ReportedReviewResponse(
        Long feedbackId,
        String brief,
        ReviewInfo review,
        PostInfo post,
        String brandName,
        String companyName,
        ReporterInfo reporter
) {
    public record ReviewInfo(
            Long reviewId,
            BigDecimal score,
            BigDecimal llmScore,
            EmotionEnum emotion,
            AspectEnum aspect
    ) {}

    public record PostInfo(
            Long postId,
            String postText,
            String postUrl,
            LanguageEnum language,
            LocalDateTime createdAt
    ) {}

    public record ReporterInfo(
            Long userId,
            String firstName,
            String lastName,
            String email
    ) {}
}
