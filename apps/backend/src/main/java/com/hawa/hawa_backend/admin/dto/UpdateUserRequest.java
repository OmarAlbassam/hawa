package com.hawa.hawa_backend.admin.dto;

import com.hawa.hawa_backend.enums.UserRoleEnum;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        Long companyId,
        UserRoleEnum role
) {}
