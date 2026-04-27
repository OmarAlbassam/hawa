import type { JSX } from "react";
import type { EmotionEnum } from "../../types/report";
import type {
  SentimentCategory,
  StatusIndicatorResponse,
} from "../../types/statusIndicator";
import "./StatusIndicator.css";

interface StatusIndicatorProps {
  data: StatusIndicatorResponse;
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

const CATEGORY_COLORS: Record<SentimentCategory, string> = {
  NEGATIVE: "#EF4444",
  NEUTRAL: "#94A3B8",
  POSITIVE: "#16A34A",
};

const toTitle = (key: string): string =>
  key.charAt(0) + key.slice(1).toLowerCase();

const fmtScore = (value: number | null): string =>
  value == null ? "—" : Math.round(value).toString();

const StatusIndicator = ({ data }: StatusIndicatorProps): JSX.Element => {
  const {
    averageSentiment,
    sentimentCategory,
    sentimentBreakdown,
    dominantEmotion,
    topEmotions,
    totalAnalyzedPosts,
  } = data;

  const isEmpty = totalAnalyzedPosts === 0 || averageSentiment == null;

  if (isEmpty) {
    return (
      <div className="StatusIndicator">
        <div className="StatusIndicator-empty">
          <p className="StatusIndicator-empty-title">No analyzed posts yet</p>
          <p className="StatusIndicator-empty-desc">
            Run an analysis to see brand sentiment and top emotions here.
          </p>
        </div>
      </div>
    );
  }

  const markerPct = Math.max(0, Math.min(100, averageSentiment));
  const categoryColor = sentimentCategory
    ? CATEGORY_COLORS[sentimentCategory]
    : "#94A3B8";

  const breakdownTotal =
    sentimentBreakdown.negative +
    sentimentBreakdown.neutral +
    sentimentBreakdown.positive;
  const negPct = breakdownTotal
    ? (sentimentBreakdown.negative / breakdownTotal) * 100
    : 0;
  const neuPct = breakdownTotal
    ? (sentimentBreakdown.neutral / breakdownTotal) * 100
    : 0;
  const posPct = breakdownTotal
    ? (sentimentBreakdown.positive / breakdownTotal) * 100
    : 0;

  return (
    <div className="StatusIndicator">
      <div className="StatusIndicator-header">
        <div className="StatusIndicator-score-block">
          <span
            className="StatusIndicator-score-value"
            style={{ color: categoryColor }}
          >
            {fmtScore(averageSentiment)}
            <span className="StatusIndicator-score-suffix"> / 100</span>
          </span>
          {sentimentCategory && (
            <span
              className="StatusIndicator-category"
              style={{
                color: categoryColor,
                borderColor: categoryColor,
              }}
            >
              {sentimentCategory}
            </span>
          )}
        </div>
        <div className="StatusIndicator-posts">
          <span className="StatusIndicator-posts-value">
            {totalAnalyzedPosts}
          </span>
          <span className="StatusIndicator-posts-label">posts analyzed</span>
        </div>
      </div>

      <div
        className="StatusIndicator-bar"
        role="img"
        aria-label={`Sentiment score ${Math.round(
          averageSentiment
        )} out of 100, ${sentimentCategory ?? "unknown"}`}
      >
        <div className="StatusIndicator-bar-track">
          <div
            className="StatusIndicator-bar-marker"
            style={{ left: `${markerPct}%` }}
          />
        </div>
        <div className="StatusIndicator-bar-scale">
          <span>0</span>
          <span>50</span>
          <span>100</span>
        </div>
      </div>

      <div className="StatusIndicator-breakdown">
        <div className="StatusIndicator-breakdown-bar">
          {negPct > 0 && (
            <div
              className="StatusIndicator-breakdown-seg StatusIndicator-breakdown-seg--neg"
              style={{ width: `${negPct}%` }}
              title={`Negative: ${sentimentBreakdown.negative}`}
            />
          )}
          {neuPct > 0 && (
            <div
              className="StatusIndicator-breakdown-seg StatusIndicator-breakdown-seg--neu"
              style={{ width: `${neuPct}%` }}
              title={`Neutral: ${sentimentBreakdown.neutral}`}
            />
          )}
          {posPct > 0 && (
            <div
              className="StatusIndicator-breakdown-seg StatusIndicator-breakdown-seg--pos"
              style={{ width: `${posPct}%` }}
              title={`Positive: ${sentimentBreakdown.positive}`}
            />
          )}
        </div>
        <ul className="StatusIndicator-breakdown-legend">
          <li>
            <span className="StatusIndicator-dot StatusIndicator-dot--neg" />
            Negative
            <strong>{sentimentBreakdown.negative}</strong>
          </li>
          <li>
            <span className="StatusIndicator-dot StatusIndicator-dot--neu" />
            Neutral
            <strong>{sentimentBreakdown.neutral}</strong>
          </li>
          <li>
            <span className="StatusIndicator-dot StatusIndicator-dot--pos" />
            Positive
            <strong>{sentimentBreakdown.positive}</strong>
          </li>
        </ul>
      </div>

      {(topEmotions.length > 0 || dominantEmotion) && (
        <div className="StatusIndicator-emotions">
          <div className="StatusIndicator-emotions-head">
            <h3 className="StatusIndicator-subtitle">Top emotions</h3>
            {dominantEmotion && (
              <div className="StatusIndicator-emotions-meta">
                <span>
                  Dominant:{" "}
                  <strong style={{ color: EMOTION_COLORS[dominantEmotion] }}>
                    {toTitle(dominantEmotion)}
                  </strong>
                </span>
              </div>
            )}
          </div>
          <ul className="StatusIndicator-emotions-list">
            {topEmotions.map((e) => (
              <li key={e.emotion} className="StatusIndicator-emotion-row">
                <span className="StatusIndicator-emotion-label">
                  {toTitle(e.emotion)}
                </span>
                <div className="StatusIndicator-emotion-bar">
                  <div
                    className="StatusIndicator-emotion-bar-fill"
                    style={{
                      width: `${e.percentage}%`,
                      backgroundColor: EMOTION_COLORS[e.emotion],
                    }}
                  />
                </div>
                <span className="StatusIndicator-emotion-pct">
                  {e.percentage.toFixed(1)}%
                </span>
                <span className="StatusIndicator-emotion-count">
                  ({e.count})
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

    </div>
  );
};

export default StatusIndicator;
