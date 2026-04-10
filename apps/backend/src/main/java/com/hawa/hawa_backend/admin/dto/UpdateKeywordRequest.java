package com.hawa.hawa_backend.admin.dto;

import com.hawa.hawa_backend.enums.KeywordTypeEnum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateKeywordRequest(
        @NotBlank String keyword,
        @NotNull KeywordTypeEnum keywordType
) {}
