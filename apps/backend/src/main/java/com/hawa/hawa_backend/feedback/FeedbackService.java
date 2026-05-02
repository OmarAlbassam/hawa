package com.hawa.hawa_backend.feedback;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.feedback.dto.CreateFeedbackRequest;
import com.hawa.hawa_backend.feedback.dto.FeedbackResponse;
import com.hawa.hawa_backend.review.Review;
import com.hawa.hawa_backend.review.ReviewRepository;
import com.hawa.hawa_backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ReviewRepository reviewRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional
    public FeedbackResponse submitFeedback(Long reviewId, CreateFeedbackRequest request) {
        User currentUser = authenticatedUserService.getAuthenticatedUser();
        Long companyId = currentUser.getCompany().getCompanyId();

        Review review = reviewRepository.findById(reviewId)
                .filter(r -> r.getPost().getReport().getBrand().getCompany()
                        .getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review not found: " + reviewId));

        Feedback feedback = feedbackRepository
                .findByUser_UserIdAndReview_ReviewId(currentUser.getUserId(), reviewId)
                .map(existing -> {
                    existing.setBrief(request.brief());
                    return existing;
                })
                .orElseGet(() -> Feedback.builder()
                        .review(review)
                        .user(currentUser)
                        .brief(request.brief())
                        .build());

        feedback = feedbackRepository.save(feedback);
        log.info("Feedback submitted: feedbackId={} userId={} reviewId={}",
                feedback.getFeedbackId(), currentUser.getUserId(), reviewId);
        return FeedbackMapper.toResponse(feedback);
    }
}
