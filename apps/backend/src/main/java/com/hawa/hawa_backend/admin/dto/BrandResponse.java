package com.hawa.hawa_backend.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BrandResponse(
        Long brandId,
        String brandName,
        AdminUserResponse.CompanyInfo company,
        String industry,
        BigDecimal statusIndicator,
        long keywordsCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
