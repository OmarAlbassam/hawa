package com.hawa.hawa_backend.feedback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.feedback.dto.CreateFeedbackRequest;
import com.hawa.hawa_backend.feedback.dto.FeedbackResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/{reviewId}/feedback")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable Long reviewId,
            @Valid @RequestBody CreateFeedbackRequest request) {
        FeedbackResponse response = feedbackService.submitFeedback(reviewId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
