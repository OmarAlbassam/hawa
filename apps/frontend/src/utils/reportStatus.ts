import type { ReportStatus } from "../types/dashboard";

export type BadgeVariant =
  | "default"
  | "primary"
  | "success"
  | "warning"
  | "error"
  | "info";

export const statusBadgeVariant: Record<ReportStatus, BadgeVariant> = {
  PENDING: "default",
  PROCESSING: "info",
  COMPLETED: "success",
  FAILED: "error",
};

export const isTerminalStatus = (status: ReportStatus): boolean =>
  status === "COMPLETED" || status === "FAILED";

export interface ReportSummaryBreakdown {
  analyzed?: number;
  failed?: number;
  topAspect?: string;
  topEmotion?: string;
}

export function parseReportSummary(
  summary: string | null
): ReportSummaryBreakdown {
  if (!summary) return {};
  const out: ReportSummaryBreakdown = {};
  for (const part of summary.split(",")) {
    const [rawKey, rawValue] = part.split("=");
    if (!rawKey || rawValue === undefined) continue;
    const key = rawKey.trim();
    const value = rawValue.trim();
    if (key === "analyzed") out.analyzed = Number(value);
    else if (key === "failed") out.failed = Number(value);
    else if (key === "top_aspect") out.topAspect = value;
    else if (key === "top_emotion") out.topEmotion = value;
  }
  return out;
}
