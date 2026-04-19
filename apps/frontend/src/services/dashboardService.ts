import { API_BASE_URL } from "../config/api";
import type { DashboardResponse } from "../types/dashboard";

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("accessToken");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function getDashboard(
  recentReportsLimit = 5
): Promise<DashboardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/dashboard?recentReportsLimit=${recentReportsLimit}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch dashboard");
  }
  return response.json();
}
