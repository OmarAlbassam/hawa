export interface BrandSummaryResponse {
  brandId: number;
  brandName: string;
  industry: string | null;
  statusIndicator: number | null;
  keywordCount: number;
  createdAt: string;
}

export interface BrandDetailResponse {
  brandId: number;
  brandName: string;
  industry: string | null;
  statusIndicator: number | null;
  keywords: KeywordInfo[];
  createdAt: string;
  updatedAt: string;
}

export interface KeywordInfo {
  keywordId: number;
  keyword: string;
  keywordType: "BRAND_NAME" | "PRODUCT" | "HASHTAG";
}
