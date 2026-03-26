package com.hawa.hawa_backend.auth.dto;

import com.hawa.hawa_backend.enums.UserRoleEnum;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            Long userId,
            String email,
            UserRoleEnum role,
            String firstName,
            String lastName
    ) {}
}
