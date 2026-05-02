package com.hawa.hawa_backend.feedback;

import com.hawa.hawa_backend.feedback.dto.FeedbackResponse;

public final class FeedbackMapper {

    private FeedbackMapper() {}

    public static FeedbackResponse toResponse(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getFeedbackId(),
                feedback.getReview().getReviewId(),
                feedback.getBrief());
    }
}
