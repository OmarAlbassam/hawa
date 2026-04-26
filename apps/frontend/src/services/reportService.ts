import { API_BASE_URL } from "../config/api";
import type { Page } from "../types/page";
import type {
  PostListItemResponse,
  ReportPostsParams,
  ReportResponse,
  ReportStatusResponse,
  ReportListParams,
  ReportOverviewResponse,
  StartAnalysisRequest,
} from "../types/report";

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("accessToken");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function getReports(
  params: ReportListParams = {}
): Promise<Page<ReportResponse>> {
  const searchParams = new URLSearchParams();
  if (params.brandId != null) searchParams.set("brandId", String(params.brandId));
  if (params.status) searchParams.set("status", params.status);
  if (params.dateFrom) searchParams.set("dateFrom", params.dateFrom);
  if (params.dateTo) searchParams.set("dateTo", params.dateTo);
  searchParams.set("page", String(params.page ?? 0));
  searchParams.set("size", String(params.size ?? 20));
  searchParams.set("sort", params.sort ?? "createdAt,desc");

  const response = await fetch(
    `${API_BASE_URL}/api/reports?${searchParams.toString()}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch reports");
  }
  return response.json();
}

export async function startAnalysis(
  brandId: number,
  data: StartAnalysisRequest
): Promise<ReportResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/brands/${brandId}/reports`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to start analysis");
  }
  return response.json();
}

export async function getReportStatus(
  reportId: number
): Promise<ReportStatusResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/reports/${reportId}/status`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch report status");
  }
  return response.json();
}

export async function getReportPosts(
  reportId: number,
  params: ReportPostsParams = {}
): Promise<Page<PostListItemResponse>> {
  const searchParams = new URLSearchParams();
  if (params.relevance) searchParams.set("relevance", params.relevance);
  if (params.sentimentMin != null) searchParams.set("sentimentMin", String(params.sentimentMin));
  if (params.sentimentMax != null) searchParams.set("sentimentMax", String(params.sentimentMax));
  if (params.emotion) searchParams.set("emotion", params.emotion);
  if (params.aspect) searchParams.set("aspect", params.aspect);
  searchParams.set("page", String(params.page ?? 0));
  searchParams.set("size", String(params.size ?? 20));
  if (params.sort) searchParams.set("sort", params.sort);

  const response = await fetch(
    `${API_BASE_URL}/api/reports/${reportId}/posts?${searchParams.toString()}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch report posts");
  }
  return response.json();
}

export async function getReportOverview(
  reportId: number
): Promise<ReportOverviewResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/reports/${reportId}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch report overview");
  }
  return response.json();
}
