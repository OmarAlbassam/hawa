import { API_BASE_URL } from "../config/api";
import type { CreateFeedbackRequest, FeedbackResponse } from "../types/feedback";

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("accessToken");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function submitFeedback(
  reviewId: number,
  data: CreateFeedbackRequest
): Promise<FeedbackResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/reviews/${reviewId}/feedback`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(
      error?.message || "Could not submit feedback. Try again later."
    );
  }
  return response.json();
}
