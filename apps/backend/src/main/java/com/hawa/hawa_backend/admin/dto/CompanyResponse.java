package com.hawa.hawa_backend.admin.dto;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long companyId,
        String companyName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
