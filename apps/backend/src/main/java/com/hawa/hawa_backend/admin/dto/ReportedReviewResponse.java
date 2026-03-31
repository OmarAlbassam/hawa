package com.hawa.hawa_backend.admin.dto;

import java.math.BigDecimal;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;

public record ReportedReviewResponse(
        Long feedbackId,
        String brief,
        ReviewInfo review,
        String postText,
        String brandName,
        String companyName,
        ReporterInfo reporter
) {
    public record ReviewInfo(
            Long reviewId,
            BigDecimal score,
            BigDecimal llmScore,
            EmotionEnum emotion,
            AspectEnum aspect,
            BigDecimal confidence
    ) {}

    public record ReporterInfo(
            Long userId,
            String firstName,
            String lastName,
            String email
    ) {}
}
