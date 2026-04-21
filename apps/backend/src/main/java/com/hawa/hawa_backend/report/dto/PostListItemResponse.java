package com.hawa.hawa_backend.report.dto;

import java.math.BigDecimal;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.IrrelevanceReasonEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.RelevanceStatusEnum;

public record PostListItemResponse(
        Long postId,
        String postText,
        String postUrl,
        LanguageEnum language,
        RelevanceStatusEnum relevanceStatus,
        IrrelevanceReasonEnum irrelevanceReason,
        BigDecimal score,
        BigDecimal confidence,
        EmotionEnum emotion,
        AspectEnum aspect
) {}
