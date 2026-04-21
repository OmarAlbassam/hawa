import type { DataSource, ReportStatus } from "./dashboard";

export interface StartAnalysisRequest {
  dataSource: DataSource;
  dateFrom?: string;
  dateTo?: string;
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
  averageSentiment: number | null;
  averageConfidence: number | null;
  emotionDistribution: Record<EmotionEnum, number>;
  aspectDistribution: Record<AspectEnum, number>;
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
