export interface CreateFeedbackRequest {
  brief: string;
}

export interface FeedbackResponse {
  feedbackId: number;
  reviewId: number;
  brief: string;
}

export const FEEDBACK_BRIEF_MIN = 5;
export const FEEDBACK_BRIEF_MAX = 1000;
