import { API_BASE_URL } from "../config/api";
import type { Page } from "../types/page";
import type {
  BrandSummaryResponse,
  BrandDetailResponse,
  KeywordInfo,
} from "../types/brand";

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem("accessToken");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function getBrands(
  page = 0,
  size = 20
): Promise<Page<BrandSummaryResponse>> {
  const response = await fetch(
    `${API_BASE_URL}/api/brands?page=${page}&size=${size}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch brands");
  }
  return response.json();
}

export async function getBrand(brandId: number): Promise<BrandDetailResponse> {
  const response = await fetch(`${API_BASE_URL}/api/brands/${brandId}`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch brand");
  }
  return response.json();
}

// ── Keywords ──

export interface KeywordRequest {
  keyword: string;
  keywordType: "BRAND_NAME" | "PRODUCT" | "HASHTAG";
}

export async function getKeywords(
  brandId: number,
  page = 0,
  size = 20
): Promise<Page<KeywordInfo>> {
  const response = await fetch(
    `${API_BASE_URL}/api/brands/${brandId}/keywords?page=${page}&size=${size}&sort=createdAt,desc`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to fetch keywords");
  }
  return response.json();
}

export async function createKeyword(
  brandId: number,
  data: KeywordRequest
): Promise<KeywordInfo> {
  const response = await fetch(
    `${API_BASE_URL}/api/brands/${brandId}/keywords`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to create keyword");
  }
  return response.json();
}

export async function updateKeyword(
  brandId: number,
  keywordId: number,
  data: KeywordRequest
): Promise<KeywordInfo> {
  const response = await fetch(
    `${API_BASE_URL}/api/brands/${brandId}/keywords/${keywordId}`,
    {
      method: "PUT",
      headers: getAuthHeaders(),
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to update keyword");
  }
  return response.json();
}

export async function deleteKeyword(
  brandId: number,
  keywordId: number
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/brands/${brandId}/keywords/${keywordId}`,
    { method: "DELETE", headers: getAuthHeaders() }
  );
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || "Failed to delete keyword");
  }
}
