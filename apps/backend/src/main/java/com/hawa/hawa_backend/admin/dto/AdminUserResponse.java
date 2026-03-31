package com.hawa.hawa_backend.admin.dto;

import java.time.LocalDateTime;

import com.hawa.hawa_backend.enums.UserRoleEnum;

public record AdminUserResponse(
        Long userId,
        String firstName,
        String lastName,
        String email,
        UserRoleEnum role,
        CompanyInfo company,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record CompanyInfo(
            Long companyId,
            String companyName
    ) {}
}
