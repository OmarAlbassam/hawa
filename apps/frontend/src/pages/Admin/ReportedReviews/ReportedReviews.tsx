import { useState, useEffect, useCallback } from "react";
import { useAuth } from "../../../context/useAuth";
import { getReportedReviews } from "../../../services/adminService";
import type { Page, ReportedReviewResponse } from "../../../types/admin";
import DataTable from "../../../components/DataTable/DataTable";
import type { Column } from "../../../components/DataTable/DataTable";
import Badge from "../../../components/Badge/Badge";
import ErrorBanner from "../../../components/ErrorBanner/ErrorBanner";
import Modal from "../../../components/Modal/Modal";
import { formatDate } from "../../../utils/formatDate";
import "./ReportedReviews.css";

function truncate(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + "..." : text;
}

const fmtScore = (value: number | null | undefined): string =>
  value == null ? "—" : Number(value).toFixed(1);

const toTitle = (key: string): string =>
  key.charAt(0) + key.slice(1).toLowerCase();

const ReportedReviews = (): React.JSX.Element => {
  const { accessToken } = useAuth();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<ReportedReviewResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<ReportedReviewResponse | null>(null);

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

  const columns: Column<ReportedReviewResponse>[] = [
    {
      key: "postText",
      header: "Post",
      render: (row) => (
        <button
          type="button"
          className="reviews-text-trigger"
          onClick={() => setSelected(row)}
          title="View full post"
        >
          <span className="reviews-text">
            {row.post.postText || "(empty)"}
          </span>
        </button>
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
      key: "emotion",
      header: "Emotion",
      render: (row) =>
        row.review.emotion ? (
          <Badge variant="info">{toTitle(row.review.emotion)}</Badge>
        ) : (
          "—"
        ),
    },
    {
      key: "aspect",
      header: "Aspect",
      render: (row) => <Badge variant="default">{toTitle(row.review.aspect)}</Badge>,
    },
    {
      key: "brief",
      header: "Feedback",
      render: (row) => (
        <span title={row.brief}>{truncate(row.brief, 60)}</span>
      ),
    },
  ];

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

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title="Post details"
        width="md"
      >
        {selected && (
          <div className="reviews-modal">
            <dl className="reviews-modal-meta">
              <div>
                <dt>Sentiment</dt>
                <dd>{fmtScore(selected.review.score)} / 5</dd>
              </div>
              <div>
                <dt>LLM Score</dt>
                <dd>{fmtScore(selected.review.llmScore)} / 5</dd>
              </div>
              <div>
                <dt>Emotion</dt>
                <dd>
                  {selected.review.emotion
                    ? toTitle(selected.review.emotion)
                    : "—"}
                </dd>
              </div>
              <div>
                <dt>Aspect</dt>
                <dd>
                  {selected.review.aspect ? toTitle(selected.review.aspect) : "—"}
                </dd>
              </div>
              <div>
                <dt>Language</dt>
                <dd>{selected.post.language}</dd>
              </div>
              <div>
                <dt>Collected</dt>
                <dd>{formatDate(selected.post.createdAt)}</dd>
              </div>
              {selected.post.postUrl && (
                <div>
                  <dt>Source</dt>
                  <dd>
                    <a
                      href={selected.post.postUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {selected.post.postUrl}
                    </a>
                  </dd>
                </div>
              )}
            </dl>

            <p className="reviews-modal-body">
              {selected.post.postText || "(empty)"}
            </p>

            <section className="reviews-modal-section">
              <h3 className="reviews-modal-section-title">Report</h3>
              <dl className="reviews-modal-meta">
                <div>
                  <dt>Brand</dt>
                  <dd>{selected.brandName}</dd>
                </div>
                <div>
                  <dt>Company</dt>
                  <dd>{selected.companyName}</dd>
                </div>
                <div>
                  <dt>Reporter</dt>
                  <dd>
                    {selected.reporter.firstName} {selected.reporter.lastName}
                  </dd>
                </div>
                <div>
                  <dt>Email</dt>
                  <dd>
                    <a href={`mailto:${selected.reporter.email}`}>
                      {selected.reporter.email}
                    </a>
                  </dd>
                </div>
              </dl>
              <p className="reviews-modal-brief-label">Reporter's feedback</p>
              <p className="reviews-modal-brief">{selected.brief}</p>
            </section>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ReportedReviews;
