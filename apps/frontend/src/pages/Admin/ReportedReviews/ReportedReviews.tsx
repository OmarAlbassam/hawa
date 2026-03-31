import { useState, useEffect, useCallback } from "react";
import { useAuth } from "../../../context/useAuth";
import { getReportedReviews } from "../../../services/adminService";
import type { Page, ReportedReviewResponse } from "../../../types/admin";
import DataTable from "../../../components/DataTable/DataTable";
import type { Column } from "../../../components/DataTable/DataTable";
import Badge from "../../../components/Badge/Badge";
import ErrorBanner from "../../../components/ErrorBanner/ErrorBanner";
import "./ReportedReviews.css";

function truncate(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + "..." : text;
}

const columns: Column<ReportedReviewResponse>[] = [
  {
    key: "postText",
    header: "Post",
    render: (row) => (
      <span className="reviews-post-text" title={row.postText}>
        {truncate(row.postText, 80)}
      </span>
    ),
  },
  { key: "brandName", header: "Brand" },
  { key: "companyName", header: "Company" },
  {
    key: "reporter",
    header: "Reporter",
    render: (row) => `${row.reporter.firstName} ${row.reporter.lastName}`,
  },
  {
    key: "score",
    header: "Score",
    render: (row) => row.review.score.toFixed(1),
  },
  {
    key: "llmScore",
    header: "LLM Score",
    render: (row) =>
      row.review.llmScore != null ? row.review.llmScore.toFixed(1) : "—",
  },
  {
    key: "emotion",
    header: "Emotion",
    render: (row) =>
      row.review.emotion ? (
        <Badge variant="info">{row.review.emotion}</Badge>
      ) : (
        "—"
      ),
  },
  {
    key: "aspect",
    header: "Aspect",
    render: (row) => <Badge variant="default">{row.review.aspect}</Badge>,
  },
  {
    key: "brief",
    header: "Feedback",
    render: (row) => (
      <span title={row.brief}>{truncate(row.brief, 60)}</span>
    ),
  },
];

const ReportedReviews = (): React.JSX.Element => {
  const { accessToken } = useAuth();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<ReportedReviewResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchReviews = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError("");
    try {
      const result = await getReportedReviews(accessToken, page);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reviews");
    } finally {
      setLoading(false);
    }
  }, [accessToken, page]);

  useEffect(() => {
    fetchReviews();
  }, [fetchReviews]);

  return (
    <div className="reviews-page">
      <h1 className="reviews-title">Reported Reviews</h1>

      {error && <ErrorBanner message={error} onRetry={fetchReviews} />}

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        keyField="feedbackId"
        page={page}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No reported reviews"
      />
    </div>
  );
};

export default ReportedReviews;
