import { useState, useEffect, useCallback, useRef } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { getReportStatus, getReports } from "../../services/reportService";
import type {
  ReportResponse,
  ReportStatusResponse,
} from "../../types/report";
import Badge from "../../components/Badge/Badge";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { formatDate } from "../../utils/formatDate";
import {
  statusBadgeVariant,
  isTerminalStatus,
  parseReportSummary,
} from "../../utils/reportStatus";
import "./ReportStatus.css";

const POLL_INTERVAL_MS = 2000;

const sourceLabel = (source: ReportResponse["dataSource"]): string =>
  source === "CSV_UPLOAD" ? "CSV upload" : "Reddit";

const ReportStatus = (): React.JSX.Element => {
  const { reportId: reportIdParam } = useParams<{ reportId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const reportId = reportIdParam ? Number(reportIdParam) : NaN;

  const seed = (location.state as { report?: ReportResponse } | null)?.report;

  const [report, setReport] = useState<ReportResponse | null>(seed ?? null);
  const [status, setStatus] = useState<ReportStatusResponse | null>(
    seed
      ? {
          reportId: seed.reportId,
          status: seed.status,
          createdAt: seed.createdAt,
          finishedAt: seed.finishedAt,
          failureReason: null,
        }
      : null
  );
  const [error, setError] = useState<string | null>(null);
  const [initialLoading, setInitialLoading] = useState(!seed);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const fetchStatus = useCallback(async (): Promise<
    ReportStatusResponse | null
  > => {
    if (Number.isNaN(reportId)) return null;
    try {
      const next = await getReportStatus(reportId);
      if (mountedRef.current) {
        setStatus(next);
        setError(null);
      }
      return next;
    } catch (err) {
      if (mountedRef.current) {
        setError(
          err instanceof Error ? err.message : "Failed to fetch report status"
        );
      }
      return null;
    }
  }, [reportId]);

  const hydrateReportFromList = useCallback(async () => {
    if (Number.isNaN(reportId)) return;
    try {
      const page = await getReports({ size: 50 });
      if (!mountedRef.current) return;
      const match = page.content.find((r) => r.reportId === reportId);
      if (match) setReport(match);
    } catch {
      // non-fatal — status polling still works without the full report
    }
  }, [reportId]);

  useEffect(() => {
    if (Number.isNaN(reportId)) return;
    let cancelled = false;
    (async () => {
      const first = await fetchStatus();
      if (cancelled || !mountedRef.current) return;
      setInitialLoading(false);
      if (!seed) await hydrateReportFromList();
      if (first && isTerminalStatus(first.status) && !seed) {
        // ensure report details (score/summary) are loaded for terminal cold load
        await hydrateReportFromList();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reportId, fetchStatus, hydrateReportFromList, seed]);

  useEffect(() => {
    if (!status || isTerminalStatus(status.status)) return;
    const id = window.setInterval(async () => {
      const next = await fetchStatus();
      if (next && isTerminalStatus(next.status)) {
        await hydrateReportFromList();
      }
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [status, fetchStatus, hydrateReportFromList]);

  if (Number.isNaN(reportId)) {
    return <ErrorBanner message="Invalid report id" />;
  }

  if (initialLoading) {
    return <div className="report-status-loading">Loading report...</div>;
  }

  const currentStatus = status?.status ?? report?.status ?? "PENDING";
  const summary = parseReportSummary(report?.summary ?? null);
  const brandName = report?.brandName;
  const source = report?.dataSource;

  return (
    <div className="report-status">
      <button
        className="report-status-back"
        onClick={() => navigate("/reports")}
        type="button"
      >
        <ArrowLeft size={16} />
        Back to Reports
      </button>

      <div className="report-status-header">
        <div>
          <h1 className="report-status-title">
            {brandName ? `${brandName} analysis` : `Report #${reportId}`}
          </h1>
          <div className="report-status-meta">
            {source && <span>Source: {sourceLabel(source)}</span>}
            {report?.dateFrom && report?.dateTo && (
              <span>
                {formatDate(report.dateFrom)} – {formatDate(report.dateTo)}
              </span>
            )}
            {status?.createdAt && (
              <span>Started {formatDate(status.createdAt)}</span>
            )}
          </div>
        </div>
        <Badge variant={statusBadgeVariant[currentStatus]}>
          {currentStatus}
        </Badge>
      </div>

      {error && <ErrorBanner message={error} onRetry={fetchStatus} />}

      {!isTerminalStatus(currentStatus) && (
        <div className="report-status-card report-status-running">
          <div className="report-status-spinner" aria-hidden />
          <div>
            <p className="report-status-running-title">
              {currentStatus === "PENDING"
                ? "Waiting for the job to start..."
                : "Analyzing posts..."}
            </p>
            <p className="report-status-running-desc">
              This page refreshes automatically. You can leave and come back
              later — the run continues in the background.
            </p>
          </div>
        </div>
      )}

      {currentStatus === "COMPLETED" && (
        <div className="report-status-card">
          <h2 className="report-status-section">Results</h2>
          <div className="report-status-stats">
            <div className="report-status-stat">
              <span className="report-status-stat-label">Sentiment score</span>
              <span className="report-status-stat-value">
                {report?.score != null ? report.score.toFixed(1) : "—"}
                <span className="report-status-stat-suffix">/ 5</span>
              </span>
            </div>
            <div className="report-status-stat">
              <span className="report-status-stat-label">Posts analyzed</span>
              <span className="report-status-stat-value">
                {summary.analyzed ?? "—"}
              </span>
            </div>
            <div className="report-status-stat">
              <span className="report-status-stat-label">Failed</span>
              <span className="report-status-stat-value">
                {summary.failed ?? "—"}
              </span>
            </div>
            <div className="report-status-stat">
              <span className="report-status-stat-label">Top aspect</span>
              <span className="report-status-stat-value">
                {summary.topAspect ?? "—"}
              </span>
            </div>
            <div className="report-status-stat">
              <span className="report-status-stat-label">Top emotion</span>
              <span className="report-status-stat-value">
                {summary.topEmotion ?? "—"}
              </span>
            </div>
          </div>
          {status?.finishedAt && (
            <p className="report-status-finished">
              Completed {formatDate(status.finishedAt)}
            </p>
          )}
        </div>
      )}

      {currentStatus === "FAILED" && (
        <ErrorBanner
          message={
            status?.failureReason ||
            "Analysis failed. Please try again or contact support."
          }
        />
      )}

      <div className="report-status-actions">
        <button
          className="report-status-secondary"
          type="button"
          onClick={() => navigate("/reports")}
        >
          View all reports
        </button>
        <button
          className="report-status-primary"
          type="button"
          onClick={() => navigate("/brands")}
        >
          Back to brands
        </button>
      </div>
    </div>
  );
};

export default ReportStatus;
