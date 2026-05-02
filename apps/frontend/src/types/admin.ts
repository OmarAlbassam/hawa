export type { Page } from "./page";

export interface AdminUserResponse {
  userId: number;
  firstName: string;
  lastName: string;
  email: string;
  role: "ADMIN" | "MARKETING_USER";
  company: { companyId: number; companyName: string } | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  companyId: number;
  role: "ADMIN" | "MARKETING_USER";
}

export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  companyId?: number | null;
  role?: "ADMIN" | "MARKETING_USER" | null;
}

export interface SystemAnalyticsResponse {
  totalUsers: number;
  totalCompanies: number;
  totalReports: number;
  reportsByStatus: {
    PENDING: number;
    PROCESSING: number;
    COMPLETED: number;
    FAILED: number;
  };
  totalPostsAnalyzed: number;
}

export interface ReportedReviewResponse {
  feedbackId: number;
  brief: string;
  review: {
    reviewId: number;
    score: number;
    llmScore: number | null;
    emotion:
      | "JOY"
      | "ANGER"
      | "SADNESS"
      | "FEAR"
      | "SURPRISE"
      | "DISGUST"
      | "NEUTRAL"
      | null;
    aspect: string;
  };
  post: {
    postId: number;
    postText: string;
    postUrl: string | null;
    language: "EN" | "AR";
    createdAt: string;
  };
  brandName: string;
  companyName: string;
  reporter: {
    userId: number;
    firstName: string;
    lastName: string;
    email: string;
  };
}

export interface UserListParams {
  search?: string;
  role?: "ADMIN" | "MARKETING_USER";
  companyId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

// ── Companies ──

export interface CompanyResponse {
  companyId: number;
  companyName: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCompanyRequest {
  companyName: string;
}

export interface UpdateCompanyRequest {
  companyName: string;
}

// ── Brands ──

export interface BrandResponse {
  brandId: number;
  brandName: string;
  company: { companyId: number; companyName: string };
  industry: string | null;
  statusIndicator: number | null;
  keywordsCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBrandRequest {
  brandName: string;
  companyId: number;
  industry?: string;
}

export interface UpdateBrandRequest {
  brandName: string;
  companyId?: number | null;
  industry?: string | null;
}

export interface BrandListParams {
  companyId?: number;
  page?: number;
  size?: number;
}

// ── Keywords ──

export type KeywordType = "BRAND_NAME" | "PRODUCT" | "HASHTAG";

export interface KeywordResponse {
  keywordId: number;
  brandId: number;
  keyword: string;
  keywordType: KeywordType;
  createdAt: string;
  updatedAt: string;
}

export interface CreateKeywordRequest {
  keyword: string;
  keywordType: KeywordType;
}

export interface UpdateKeywordRequest {
  keyword: string;
  keywordType: KeywordType;
}
