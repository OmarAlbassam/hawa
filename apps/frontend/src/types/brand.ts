import type {
  AspectShare,
  EmotionEnum,
  EmotionShare,
  SentimentBreakdown,
} from "./report";

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

export type KeywordType = "BRAND_NAME" | "PRODUCT" | "MISSPELLING" | "OTHER";

export const KEYWORD_TYPES: KeywordType[] = [
  "BRAND_NAME",
  "PRODUCT",
  "MISSPELLING",
  "OTHER",
];

export const KEYWORD_TYPE_LABELS: Record<KeywordType, string> = {
  BRAND_NAME: "Brand Name",
  PRODUCT: "Product",
  MISSPELLING: "Misspelling",
  OTHER: "Other",
};

export interface KeywordInfo {
  keywordId: number;
  keyword: string;
  keywordType: KeywordType;
}

export interface BrandStatusIndicatorResponse {
  brandId: number;
  brandName: string;
  completedReportCount: number;
  averageSentiment: number | null;
  analyzedPostCount: number;
  sentimentBreakdown: SentimentBreakdown;
  dominantEmotion: EmotionEnum | null;
  topEmotions: EmotionShare[];
  topAspects: AspectShare[];
}
