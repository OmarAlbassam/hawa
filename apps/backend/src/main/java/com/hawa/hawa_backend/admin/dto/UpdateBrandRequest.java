package com.hawa.hawa_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBrandRequest(
        @NotBlank String brandName,
        Long companyId,
        String industry
) {}
