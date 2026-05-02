package com.hawa.hawa_backend.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
        @NotBlank
        @Size(min = 5, max = 1000)
        String brief
) {}
