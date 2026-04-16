package com.hawa.hawa_backend.brand.dto;

import com.hawa.hawa_backend.enums.KeywordTypeEnum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateKeywordRequest(
        @NotBlank String keyword,
        @NotNull KeywordTypeEnum keywordType
) {}
