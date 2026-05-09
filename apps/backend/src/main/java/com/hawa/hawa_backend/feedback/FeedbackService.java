package com.hawa.hawa_backend.feedback;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    public FeedbackResponse submitFeedback(Long reviewId, CreateFeedbackRequest request) {
        User currentUser = authenticatedUserService.getAuthenticatedUser();
        Long companyId = authenticatedUserService.getCompanyId();

        try {
            return transactionTemplate.execute(s -> doUpsert(currentUser, companyId, reviewId, request));
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent feedback insert detected, retrying upsert: userId={} reviewId={}",
                    currentUser.getUserId(), reviewId);
            return transactionTemplate.execute(s -> doUpsert(currentUser, companyId, reviewId, request));
        }
    }

    private FeedbackResponse doUpsert(User currentUser, Long companyId, Long reviewId,
                                       CreateFeedbackRequest request) {
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
