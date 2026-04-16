package com.hawa.hawa_backend.brand.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.hawa.hawa_backend.enums.KeywordTypeEnum;

public record BrandDetailResponse(
        Long brandId,
        String brandName,
        String industry,
        BigDecimal statusIndicator,
        List<KeywordInfo> keywords,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record KeywordInfo(
            Long keywordId,
            String keyword,
            KeywordTypeEnum keywordType
    ) {}
}
