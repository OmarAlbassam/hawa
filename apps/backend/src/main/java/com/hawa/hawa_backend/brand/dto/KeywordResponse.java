package com.hawa.hawa_backend.brand.dto;

import java.time.LocalDateTime;

import com.hawa.hawa_backend.enums.KeywordTypeEnum;

public record KeywordResponse(
        Long keywordId,
        Long brandId,
        String keyword,
        KeywordTypeEnum keywordType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
