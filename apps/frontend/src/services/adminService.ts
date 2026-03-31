import { API_BASE_URL } from "../config/api";
import type {
  Page,
  AdminUserResponse,
  CreateUserRequest,
  UpdateUserRequest,
  SystemAnalyticsResponse,
  ReportedReviewResponse,
  UserListParams,
  CompanyResponse,
  CreateCompanyRequest,
  UpdateCompanyRequest,
  BrandResponse,
  CreateBrandRequest,
  UpdateBrandRequest,
  BrandListParams,
} from "../types/admin";

function authHeaders(token: string): Record<string, string> {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

async function handleError(response: Response, fallback: string): Promise<never> {
  if (response.status === 409) {
    throw new Error("Email already in use");
  }
  if (response.status === 404) {
    throw new Error("Resource not found");
  }
  if (response.status === 401) {
    throw new Error("Session expired. Please log in again.");
  }
  if (response.status === 403) {
    throw new Error("Access denied. Admin privileges required.");
  }
  const error = await response.json().catch(() => null);
  throw new Error(error?.message || fallback);
}

export async function getUsers(
  token: string,
  params: UserListParams = {}
): Promise<Page<AdminUserResponse>> {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.role) query.set("role", params.role);
  if (params.companyId != null) query.set("companyId", String(params.companyId));
  if (params.page != null) query.set("page", String(params.page));
  if (params.size != null) query.set("size", String(params.size));
  if (params.sort) query.set("sort", params.sort);

  const qs = query.toString();
  const response = await fetch(
    `${API_BASE_URL}/api/admin/users${qs ? `?${qs}` : ""}`,
    { headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to fetch users");
  return response.json();
}

export async function getUser(
  token: string,
  userId: number
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}`, {
    headers: authHeaders(token),
  });
  if (!response.ok) return handleError(response, "Failed to fetch user");
  return response.json();
}

export async function createUser(
  token: string,
  data: CreateUserRequest
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(data),
  });
  if (!response.ok) return handleError(response, "Failed to create user");
  return response.json();
}

export async function updateUser(
  token: string,
  userId: number,
  data: UpdateUserRequest
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}`, {
    method: "PUT",
    headers: authHeaders(token),
    body: JSON.stringify(data),
  });
  if (!response.ok) return handleError(response, "Failed to update user");
  return response.json();
}

export async function deleteUser(
  token: string,
  userId: number
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}`, {
    method: "DELETE",
    headers: authHeaders(token),
  });
  if (!response.ok) {
    if (response.status === 400) {
      throw new Error("Cannot delete your own account");
    }
    return handleError(response, "Failed to delete user");
  }
}

export async function getAnalytics(
  token: string
): Promise<SystemAnalyticsResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/analytics`, {
    headers: authHeaders(token),
  });
  if (!response.ok) return handleError(response, "Failed to fetch analytics");
  return response.json();
}

export async function getReportedReviews(
  token: string,
  page: number = 0,
  size: number = 20
): Promise<Page<ReportedReviewResponse>> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/reported-reviews?page=${page}&size=${size}`,
    { headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to fetch reported reviews");
  return response.json();
}

// ── Companies ──

export async function getCompanies(
  token: string,
  page: number = 0,
  size: number = 20
): Promise<Page<CompanyResponse>> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/companies?page=${page}&size=${size}`,
    { headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to fetch companies");
  return response.json();
}

export async function getCompany(
  token: string,
  companyId: number
): Promise<CompanyResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/companies/${companyId}`,
    { headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to fetch company");
  return response.json();
}

export async function createCompany(
  token: string,
  data: CreateCompanyRequest
): Promise<CompanyResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/companies`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(data),
  });
  if (!response.ok) return handleError(response, "Failed to create company");
  return response.json();
}

export async function updateCompany(
  token: string,
  companyId: number,
  data: UpdateCompanyRequest
): Promise<CompanyResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/companies/${companyId}`,
    {
      method: "PUT",
      headers: authHeaders(token),
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) return handleError(response, "Failed to update company");
  return response.json();
}

export async function deleteCompany(
  token: string,
  companyId: number
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/companies/${companyId}`,
    { method: "DELETE", headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to delete company");
}

// ── Brands ──

export async function getBrands(
  token: string,
  params: BrandListParams = {}
): Promise<Page<BrandResponse>> {
  const query = new URLSearchParams();
  if (params.companyId != null) query.set("companyId", String(params.companyId));
  if (params.page != null) query.set("page", String(params.page));
  if (params.size != null) query.set("size", String(params.size));

  const qs = query.toString();
  const response = await fetch(
    `${API_BASE_URL}/api/admin/brands${qs ? `?${qs}` : ""}`,
    { headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to fetch brands");
  return response.json();
}

export async function getBrand(
  token: string,
  brandId: number
): Promise<BrandResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/brands/${brandId}`,
    { headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to fetch brand");
  return response.json();
}

export async function createBrand(
  token: string,
  data: CreateBrandRequest
): Promise<BrandResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/brands`, {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify(data),
  });
  if (!response.ok) return handleError(response, "Failed to create brand");
  return response.json();
}

export async function updateBrand(
  token: string,
  brandId: number,
  data: UpdateBrandRequest
): Promise<BrandResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/brands/${brandId}`,
    {
      method: "PUT",
      headers: authHeaders(token),
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) return handleError(response, "Failed to update brand");
  return response.json();
}

export async function deleteBrand(
  token: string,
  brandId: number
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/brands/${brandId}`,
    { method: "DELETE", headers: authHeaders(token) }
  );
  if (!response.ok) return handleError(response, "Failed to delete brand");
}
