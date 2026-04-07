package com.hawa.hawa_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBrandRequest(
        @NotBlank String brandName,
        @NotNull Long companyId,
        String industry
) {}
