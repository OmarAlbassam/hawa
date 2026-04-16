package com.hawa.hawa_backend.brand.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BrandSummaryResponse(
        Long brandId,
        String brandName,
        String industry,
        BigDecimal statusIndicator,
        long keywordCount,
        LocalDateTime createdAt
) {}
