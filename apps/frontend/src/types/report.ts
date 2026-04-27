import type { DataSource, ReportStatus } from "./dashboard";

export interface StartAnalysisRequest {
  dataSource: DataSource;
  dateFrom?: string;
  dateTo?: string;
  selectedKeywordIds: number[];
}

export interface ReportResponse {
  reportId: number;
  brandName: string;
  status: ReportStatus;
  dataSource: DataSource;
  score: number | null;
  summary: string | null;
  dateFrom: string | null;
  dateTo: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface ReportStatusResponse {
  reportId: number;
  status: ReportStatus;
  createdAt: string;
  finishedAt: string | null;
  failureReason: string | null;
}

export type EmotionEnum =
  | "JOY"
  | "ANGER"
  | "SADNESS"
  | "FEAR"
  | "SURPRISE"
  | "DISGUST"
  | "NEUTRAL";

export type AspectEnum = "PRODUCT" | "SERVICE" | "DELIVERY" | "PRICING";

export type LanguageEnum = "EN" | "AR";

export interface ReportOverviewResponse {
  reportId: number;
  brandName: string;
  status: ReportStatus;
  dataSource: DataSource;
  dateFrom: string | null;
  dateTo: string | null;
  createdAt: string;
  finishedAt: string | null;
  summary: string | null;
  score: number | null;
  analyzedPosts: number;
  filteredOutCount: number;
  averageSentiment: number | null;
  emotionDistribution: Record<EmotionEnum, number>;
  aspectDistribution: Record<AspectEnum, number>;
}

export type RelevanceStatus = "RELEVANT" | "IRRELEVANT";

export type IrrelevanceReason =
  | "HOMONYM"
  | "SPAM"
  | "EMPTY"
  | "WRONG_LANGUAGE"
  | "OTHER";

export interface PostListItemResponse {
  postId: number;
  postText: string;
  postUrl: string | null;
  language: LanguageEnum;
  relevanceStatus: RelevanceStatus;
  irrelevanceReason: IrrelevanceReason | null;
  score: number | null;
  emotion: EmotionEnum | null;
  aspect: AspectEnum | null;
  createdAt: string;
}

export interface ReportPostsParams {
  relevance?: RelevanceStatus;
  sentimentMin?: number;
  sentimentMax?: number;
  emotion?: EmotionEnum;
  aspect?: AspectEnum;
  language?: LanguageEnum;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ReportExportParams {
  sentimentMin?: number;
  sentimentMax?: number;
  emotion?: EmotionEnum;
  aspect?: AspectEnum;
  language?: LanguageEnum;
  dateFrom?: string;
  dateTo?: string;
}

export interface ReportListParams {
  brandId?: number;
  status?: ReportStatus;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}
