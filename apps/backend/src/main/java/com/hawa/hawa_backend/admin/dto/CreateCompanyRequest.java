package com.hawa.hawa_backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank String companyName
) {}
