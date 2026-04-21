import { useState, useEffect, useCallback, useRef } from "react";
import { useParams, useLocation, Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import {
  getReportStatus,
  getReports,
  getReportOverview,
} from "../../services/reportService";
import type {
  AspectEnum,
  EmotionEnum,
  ReportOverviewResponse,
  ReportResponse,
  ReportStatusResponse,
} from "../../types/report";
import Badge from "../../components/Badge/Badge";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { formatDate } from "../../utils/formatDate";
import {
  statusBadgeVariant,
  isTerminalStatus,
} from "../../utils/reportStatus";
import "./ReportStatus.css";

const POLL_INTERVAL_MS = 2000;

const sourceLabel = (source: ReportResponse["dataSource"]): string =>
  source === "CSV_UPLOAD" ? "CSV upload" : "Reddit";

const EMOTION_ORDER: EmotionEnum[] = [
  "JOY",
  "ANGER",
  "SADNESS",
  "FEAR",
  "SURPRISE",
  "DISGUST",
];

const EMOTION_COLORS: Record<EmotionEnum, string> = {
  JOY: "#FBBF24",
  ANGER: "#EF4444",
  SADNESS: "#3B82F6",
  FEAR: "#8B5CF6",
  SURPRISE: "#F97316",
  DISGUST: "#84CC16",
};

const ASPECT_ORDER: AspectEnum[] = [
  "PRODUCT",
  "SERVICE",
  "DELIVERY",
  "PRICING",
];

const ASPECT_COLORS: Record<AspectEnum, string> = {
  PRODUCT: "#0284C7",
  SERVICE: "#16A34A",
  DELIVERY: "#D97706",
  PRICING: "#E91E63",
};

const toTitle = (key: string): string =>
  key.charAt(0) + key.slice(1).toLowerCase();

const fmtScore = (value: number | null | undefined): string =>
  value == null ? "—" : Number(value).toFixed(1);

const fmtPct = (value: number | null | undefined): string =>
  value == null ? "—" : `${Math.round(Number(value) * 100)}%`;

interface DistributionProps<K extends string> {
  entries: Array<[K, number]>;
  colorMap: Record<K, string>;
}

function Distribution<K extends string>({
  entries,
  colorMap,
}: DistributionProps<K>): React.JSX.Element {
  const max = Math.max(1, ...entries.map(([, v]) => v));
  return (
    <div className="report-status-dist">
      {entries.map(([key, count]) => {
        const pct = (count / max) * 100;
        return (
          <div className="report-status-dist-row" key={key}>
            <span className="report-status-dist-label">{toTitle(key)}</span>
            <div className="report-status-dist-bar">
              <div
                className="report-status-dist-bar-fill"
                style={{ width: `${pct}%`, backgroundColor: colorMap[key] }}
              />
            </div>
            <span className="report-status-dist-count">{count}</span>
          </div>
        );
      })}
    </div>
  );
}

const ReportStatus = (): React.JSX.Element => {
  const { reportId: reportIdParam } = useParams<{ reportId: string }>();
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
  const [overview, setOverview] = useState<ReportOverviewResponse | null>(null);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [overviewReportId, setOverviewReportId] = useState<number>(reportId);
  if (overviewReportId !== reportId) {
    // reset when navigating between reports without remount
    setOverviewReportId(reportId);
    setOverview(null);
    setOverviewError(null);
  }
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

  const currentStatus = status?.status ?? report?.status ?? "PENDING";

  const fetchOverview = useCallback(async () => {
    if (Number.isNaN(reportId)) return;
    try {
      const data = await getReportOverview(reportId);
      if (mountedRef.current) {
        setOverview(data);
        setOverviewError(null);
      }
    } catch (err) {
      if (mountedRef.current) {
        setOverviewError(
          err instanceof Error ? err.message : "Failed to load overview"
        );
      }
    }
  }, [reportId]);

  useEffect(() => {
    if (Number.isNaN(reportId)) return;
    if (currentStatus !== "COMPLETED") return;
    if (overview != null || overviewError != null) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await getReportOverview(reportId);
        if (!cancelled && mountedRef.current) {
          setOverview(data);
          setOverviewError(null);
        }
      } catch (err) {
        if (!cancelled && mountedRef.current) {
          setOverviewError(
            err instanceof Error ? err.message : "Failed to load overview"
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [currentStatus, overview, overviewError, reportId]);

  if (Number.isNaN(reportId)) {
    return <ErrorBanner message="Invalid report id" />;
  }

  if (initialLoading) {
    return <div className="report-status-loading">Loading report...</div>;
  }

  const brandName = report?.brandName;
  const source = report?.dataSource;

  return (
    <div className="report-status">
      <Link to="/reports" className="report-status-back">
        <ArrowLeft size={16} />
        Back to Reports
      </Link>

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
        <>
          {overviewError && (
            <ErrorBanner message={overviewError} onRetry={fetchOverview} />
          )}
          {!overview && !overviewError && (
            <div className="report-status-loading">Loading overview...</div>
          )}
          {overview && (
            <>
              <div className="report-status-card">
                <h2 className="report-status-section">Overview</h2>
                <div className="report-status-stats">
                  <div className="report-status-stat">
                    <span className="report-status-stat-label">
                      Sentiment score
                    </span>
                    <span className="report-status-stat-value">
                      {fmtScore(overview.averageSentiment)}
                      <span className="report-status-stat-suffix">/ 5</span>
                    </span>
                  </div>
                  <div className="report-status-stat">
                    <span className="report-status-stat-label">
                      Average confidence
                    </span>
                    <span className="report-status-stat-value">
                      {fmtPct(overview.averageConfidence)}
                    </span>
                  </div>
                  <div className="report-status-stat">
                    <span className="report-status-stat-label">
                      Posts analyzed
                    </span>
                    <span className="report-status-stat-value">
                      {overview.analyzedPosts}
                    </span>
                  </div>
                </div>
                {status?.finishedAt && (
                  <p className="report-status-finished">
                    Completed {formatDate(status.finishedAt)}
                  </p>
                )}
              </div>

              <div className="report-status-card">
                <h2 className="report-status-section">Emotion distribution</h2>
                <Distribution
                  entries={EMOTION_ORDER.map((k) => [
                    k,
                    overview.emotionDistribution[k] ?? 0,
                  ])}
                  colorMap={EMOTION_COLORS}
                />
              </div>

              <div className="report-status-card">
                <h2 className="report-status-section">Aspect breakdown</h2>
                <Distribution
                  entries={ASPECT_ORDER.map((k) => [
                    k,
                    overview.aspectDistribution[k] ?? 0,
                  ])}
                  colorMap={ASPECT_COLORS}
                />
              </div>
            </>
          )}
        </>
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
        <Link to="/reports" className="report-status-secondary">
          View all reports
        </Link>
        <Link to="/brands" className="report-status-primary">
          Back to brands
        </Link>
      </div>
    </div>
  );
};

export default ReportStatus;
