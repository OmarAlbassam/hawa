package com.hawa.hawa_backend.feedback.dto;

public record FeedbackResponse(
        Long feedbackId,
        Long reviewId,
        String brief
) {}
