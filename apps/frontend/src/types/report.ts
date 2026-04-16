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
