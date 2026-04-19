export interface DashboardResponse {
  brands: BrandSummary[];
  recentReports: RecentReport[];
  stats: DashboardStats;
}

export interface BrandSummary {
  brandId: number;
  brandName: string;
  industry: string | null;
  statusIndicator: number | null;
}

export interface RecentReport {
  reportId: number;
  brandName: string;
  status: ReportStatus;
  dataSource: DataSource;
  score: number | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface DashboardStats {
  totalReports: number;
  totalPostsAnalyzed: number;
  totalBrands: number;
}

export type ReportStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
export type DataSource = "REDDIT" | "CSV_UPLOAD";
