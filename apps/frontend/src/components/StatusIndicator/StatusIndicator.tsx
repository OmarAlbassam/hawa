import { useCallback, useEffect, useMemo, useState } from "react";
import { getStatusIndicator } from "../../services/reportService";
import { getBrandStatusIndicator } from "../../services/brandService";
import type {
  AspectEnum,
  AspectShare,
  EmotionEnum,
  EmotionShare,
  StatusIndicatorResponse,
} from "../../types/report";
import type { BrandStatusIndicatorResponse } from "../../types/brand";
import ErrorBanner from "../ErrorBanner/ErrorBanner";
import "./StatusIndicator.css";

export type StatusIndicatorSource =
  | { kind: "report"; reportId: number }
  | { kind: "brand"; brandId: number };

interface StatusIndicatorProps {
  source: StatusIndicatorSource;
  title?: string;
  className?: string;
}

interface NormalizedStatus {
  averageSentiment: number | null;
  analyzedPostCount: number;
  topEmotions: EmotionShare[];
  topAspects: AspectShare[];
  summary: string | null;
  meta: string;
}

const EMOTION_COLORS: Record<EmotionEnum, string> = {
  JOY: "#FBBF24",
  ANGER: "#EF4444",
  SADNESS: "#3B82F6",
  FEAR: "#8B5CF6",
  SURPRISE: "#F97316",
  DISGUST: "#84CC16",
  NEUTRAL: "#9CA3AF",
};

const ASPECT_COLORS: Record<AspectEnum, string> = {
  PRODUCT: "#0284C7",
  SERVICE: "#16A34A",
  DELIVERY: "#D97706",
  PRICING: "#E91E63",
};

const toTitle = (key: string): string =>
  key.charAt(0) + key.slice(1).toLowerCase();

const fmtPct = (value: number): string => `${Math.round(value * 100)}%`;

const scoreColor = (score: number): string => {
  if (score < 20) return "#EF4444";
  if (score < 40) return "#F97316";
  if (score < 60) return "#94A3B8";
  if (score < 80) return "#22C55E";
  return "#16A34A";
};

const scoreLabel = (score: number): string => {
  if (score < 20) return "Very Negative";
  if (score < 40) return "Negative";
  if (score < 60) return "Neutral";
  if (score < 80) return "Positive";
  return "Very Positive";
};

const normalizeReport = (data: StatusIndicatorResponse): NormalizedStatus => ({
  averageSentiment: data.averageSentiment,
  analyzedPostCount: data.analyzedPostCount,
  topEmotions: data.topEmotions,
  topAspects: data.topAspects,
  summary: data.summary,
  meta: `${data.analyzedPostCount.toLocaleString()} posts analyzed`,
});

const normalizeBrand = (
  data: BrandStatusIndicatorResponse
): NormalizedStatus => ({
  averageSentiment: data.averageSentiment,
  analyzedPostCount: data.analyzedPostCount,
  topEmotions: data.topEmotions,
  topAspects: data.topAspects,
  summary: null,
  meta: `${data.completedReportCount.toLocaleString()} ${
    data.completedReportCount === 1 ? "report" : "reports"
  } · ${data.analyzedPostCount.toLocaleString()} posts`,
});

interface ScoreDialProps {
  score: number;
}

const ScoreDial = ({ score }: ScoreDialProps): React.JSX.Element => {
  const color = scoreColor(score);
  const label = scoreLabel(score);
  const ringStyle = {
    background: `conic-gradient(${color} ${score * 3.6}deg, var(--color-muted-bg) 0deg)`,
  } as React.CSSProperties;

  return (
    <div
      className="StatusIndicator-dial"
      role="img"
      aria-label={`Brand score ${score} of 100, ${label}`}
    >
      <div className="StatusIndicator-dial-ring" style={ringStyle} aria-hidden>
        <div className="StatusIndicator-dial-inner">
          <span className="StatusIndicator-dial-score" style={{ color }}>
            {score}
          </span>
          <span className="StatusIndicator-dial-suffix">/ 100</span>
        </div>
      </div>
      <span className="StatusIndicator-dial-label" style={{ color }}>
        {label}
      </span>
    </div>
  );
};

interface ChipListProps<T extends EmotionShare | AspectShare> {
  heading: string;
  items: T[];
  getKey: (item: T) => string;
  getColor: (item: T) => string;
  emptyMessage: string;
}

function ChipList<T extends EmotionShare | AspectShare>({
  heading,
  items,
  getKey,
  getColor,
  emptyMessage,
}: ChipListProps<T>): React.JSX.Element {
  return (
    <div className="StatusIndicator-chip-group">
      <h3 className="StatusIndicator-chip-heading">{heading}</h3>
      {items.length === 0 ? (
        <p className="StatusIndicator-chip-empty">{emptyMessage}</p>
      ) : (
        <ul className="StatusIndicator-chip-list">
          {items.map((item) => {
            const key = getKey(item);
            const color = getColor(item);
            return (
              <li
                key={key}
                className="StatusIndicator-chip"
                style={{
                  borderColor: color,
                  color,
                  backgroundColor: `${color}1A`,
                }}
              >
                <span className="StatusIndicator-chip-label">{toTitle(key)}</span>
                <span className="StatusIndicator-chip-meta">
                  {item.count} · {fmtPct(item.percentage)}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

const StatusIndicator = ({
  source,
  title = "Brand Status Indicator",
  className,
}: StatusIndicatorProps): React.JSX.Element => {
  const [data, setData] = useState<NormalizedStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const sourceKey =
    source.kind === "report"
      ? `report:${source.reportId}`
      : `brand:${source.brandId}`;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (source.kind === "report") {
        setData(normalizeReport(await getStatusIndicator(source.reportId)));
      } else {
        setData(normalizeBrand(await getBrandStatusIndicator(source.brandId)));
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load status indicator"
      );
    } finally {
      setLoading(false);
    }
    // sourceKey captures both kind and id; expanding source.* would re-trigger on identity churn
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey]);

  useEffect(() => {
    load();
  }, [load]);

  const score = useMemo(() => {
    if (!data || data.averageSentiment == null) return null;
    return Math.round((data.averageSentiment / 5) * 100);
  }, [data]);

  const rootClass = className
    ? `StatusIndicator ${className}`
    : "StatusIndicator";

  if (loading) {
    return (
      <section className={rootClass} aria-busy="true">
        <header className="StatusIndicator-header">
          <h2 className="StatusIndicator-title">{title}</h2>
        </header>
        <div className="StatusIndicator-skeleton" aria-hidden />
      </section>
    );
  }

  if (error) {
    return (
      <section className={rootClass}>
        <header className="StatusIndicator-header">
          <h2 className="StatusIndicator-title">{title}</h2>
        </header>
        <ErrorBanner message={error} onRetry={load} />
      </section>
    );
  }

  if (!data) return <></>;

  if (data.analyzedPostCount === 0 || score == null) {
    return (
      <section className={rootClass}>
        <header className="StatusIndicator-header">
          <h2 className="StatusIndicator-title">{title}</h2>
        </header>
        <p className="StatusIndicator-empty">
          {source.kind === "brand"
            ? "No completed reports yet for this brand."
            : "No analyzed posts yet for this report."}
        </p>
      </section>
    );
  }

  return (
    <section className={rootClass}>
      <header className="StatusIndicator-header">
        <h2 className="StatusIndicator-title">{title}</h2>
        <span className="StatusIndicator-meta">{data.meta}</span>
      </header>

      <div className="StatusIndicator-body">
        <ScoreDial score={score} />

        <div className="StatusIndicator-chips">
          <ChipList
            heading="Top emotions"
            items={data.topEmotions}
            getKey={(item) => item.emotion}
            getColor={(item) => EMOTION_COLORS[item.emotion]}
            emptyMessage="No emotions detected."
          />
          <ChipList
            heading="Top aspects"
            items={data.topAspects}
            getKey={(item) => item.aspect}
            getColor={(item) => ASPECT_COLORS[item.aspect]}
            emptyMessage="No aspects detected."
          />
        </div>
      </div>

      {data.summary && (
        <p className="StatusIndicator-summary">{data.summary}</p>
      )}
    </section>
  );
};

export default StatusIndicator;
