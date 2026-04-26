import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { getReportPosts } from "../../services/reportService";
import type {
  AspectEnum,
  EmotionEnum,
  IrrelevanceReason,
  LanguageEnum,
  PostListItemResponse,
  RelevanceStatus,
  ReportPostsParams,
} from "../../types/report";
import type { Page } from "../../types/page";
import Badge from "../../components/Badge/Badge";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import Modal from "../../components/Modal/Modal";
import { formatDate } from "../../utils/formatDate";
import "./PostsList.css";

const PAGE_SIZE = 20;

const EMOTION_OPTIONS: EmotionEnum[] = [
  "JOY",
  "ANGER",
  "SADNESS",
  "FEAR",
  "SURPRISE",
  "DISGUST",
  "NEUTRAL",
];

const ASPECT_OPTIONS: AspectEnum[] = ["PRODUCT", "SERVICE", "DELIVERY", "PRICING"];

const LANGUAGE_OPTIONS: LanguageEnum[] = ["EN", "AR"];

const BASE_SORT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "createdAt,desc", label: "Newest first" },
  { value: "createdAt,asc", label: "Oldest first" },
];

const RELEVANT_SORT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "score,desc", label: "Highest sentiment" },
  { value: "score,asc", label: "Lowest sentiment" },
  { value: "confidence,desc", label: "Highest confidence" },
];

const DEFAULT_SORT = "createdAt,desc";

const isSortAllowed = (sort: string, isIrrelevant: boolean): boolean => {
  if (!isIrrelevant) return true;
  return BASE_SORT_OPTIONS.some((opt) => opt.value === sort);
};

const REASON_LABEL: Record<IrrelevanceReason, string> = {
  HOMONYM: "Homonym",
  SPAM: "Spam",
  EMPTY: "Empty",
  WRONG_LANGUAGE: "Wrong language",
  OTHER: "Other",
};

const EMOTION_BADGE_VARIANT: Record<
  EmotionEnum,
  "default" | "primary" | "success" | "warning" | "error" | "info"
> = {
  JOY: "success",
  ANGER: "error",
  SADNESS: "info",
  FEAR: "warning",
  SURPRISE: "primary",
  DISGUST: "warning",
  NEUTRAL: "default",
};

const toTitle = (key: string): string =>
  key.charAt(0) + key.slice(1).toLowerCase();

const fmtScore = (value: number | null | undefined): string =>
  value == null ? "—" : Number(value).toFixed(1);

const fmtPct = (value: number | null | undefined): string =>
  value == null ? "—" : `${Math.round(Number(value) * 100)}%`;

interface Filters {
  relevance: RelevanceStatus;
  sentimentMin: string;
  sentimentMax: string;
  confidenceMin: string;
  confidenceMax: string;
  emotion: EmotionEnum | "";
  aspect: AspectEnum | "";
  language: LanguageEnum | "";
  dateFrom: string;
  dateTo: string;
}

const EMPTY_FILTERS: Filters = {
  relevance: "RELEVANT",
  sentimentMin: "",
  sentimentMax: "",
  confidenceMin: "",
  confidenceMax: "",
  emotion: "",
  aspect: "",
  language: "",
  dateFrom: "",
  dateTo: "",
};

const filtersFromSearch = (search: URLSearchParams): Filters => ({
  relevance: (search.get("relevance") as RelevanceStatus) || "RELEVANT",
  sentimentMin: search.get("sentimentMin") ?? "",
  sentimentMax: search.get("sentimentMax") ?? "",
  confidenceMin: search.get("confidenceMin") ?? "",
  confidenceMax: search.get("confidenceMax") ?? "",
  emotion: (search.get("emotion") as EmotionEnum) || "",
  aspect: (search.get("aspect") as AspectEnum) || "",
  language: (search.get("language") as LanguageEnum) || "",
  dateFrom: search.get("dateFrom") ?? "",
  dateTo: search.get("dateTo") ?? "",
});

const parseNumber = (value: string): number | undefined => {
  if (value === "") return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const filtersToQuery = (
  filters: Filters,
  page: number,
  sort: string
): ReportPostsParams => ({
  relevance: filters.relevance,
  sentimentMin: parseNumber(filters.sentimentMin),
  sentimentMax: parseNumber(filters.sentimentMax),
  confidenceMin: parseNumber(filters.confidenceMin),
  confidenceMax: parseNumber(filters.confidenceMax),
  emotion: filters.emotion || undefined,
  aspect: filters.aspect || undefined,
  language: filters.language || undefined,
  dateFrom: filters.dateFrom || undefined,
  dateTo: filters.dateTo || undefined,
  page,
  size: PAGE_SIZE,
  sort,
});

const PostsList = (): React.JSX.Element => {
  const { reportId: reportIdParam } = useParams<{ reportId: string }>();
  const reportId = reportIdParam ? Number(reportIdParam) : NaN;
  const [searchParams, setSearchParams] = useSearchParams();

  const appliedFilters = useMemo(
    () => filtersFromSearch(searchParams),
    [searchParams]
  );
  const pageNumber = Number(searchParams.get("page") ?? "0");
  const rawSort = searchParams.get("sort") ?? DEFAULT_SORT;
  const sort = isSortAllowed(rawSort, appliedFilters.relevance === "IRRELEVANT")
    ? rawSort
    : DEFAULT_SORT;

  const [draft, setDraft] = useState<Filters>(appliedFilters);
  const [data, setData] = useState<Page<PostListItemResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<PostListItemResponse | null>(null);

  useEffect(() => {
    setDraft(appliedFilters);
  }, [appliedFilters]);

  const load = useCallback(async () => {
    if (Number.isNaN(reportId)) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getReportPosts(
        reportId,
        filtersToQuery(appliedFilters, pageNumber, sort)
      );
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load posts");
    } finally {
      setLoading(false);
    }
  }, [reportId, appliedFilters, pageNumber, sort]);

  useEffect(() => {
    void load();
  }, [load]);

  const writeSearch = (
    next: Partial<{
      filters: Filters;
      page: number;
      sort: string;
    }>
  ) => {
    const nextParams = new URLSearchParams(searchParams);
    if (next.filters) {
      const f = next.filters;
      const writeOrDelete = (key: string, value: string) => {
        if (value) nextParams.set(key, value);
        else nextParams.delete(key);
      };
      writeOrDelete("relevance", f.relevance);
      writeOrDelete("sentimentMin", f.sentimentMin);
      writeOrDelete("sentimentMax", f.sentimentMax);
      writeOrDelete("confidenceMin", f.confidenceMin);
      writeOrDelete("confidenceMax", f.confidenceMax);
      writeOrDelete("emotion", f.emotion);
      writeOrDelete("aspect", f.aspect);
      writeOrDelete("language", f.language);
      writeOrDelete("dateFrom", f.dateFrom);
      writeOrDelete("dateTo", f.dateTo);
    }
    if (next.page != null) nextParams.set("page", String(next.page));
    if (next.sort) nextParams.set("sort", next.sort);
    setSearchParams(nextParams, { replace: false });
  };

  const applyFilters = (e?: React.FormEvent) => {
    e?.preventDefault();
    const draftIsIrrelevant = draft.relevance === "IRRELEVANT";
    const nextSort = isSortAllowed(sort, draftIsIrrelevant) ? undefined : DEFAULT_SORT;
    writeSearch({ filters: draft, page: 0, sort: nextSort });
  };

  const clearFilters = () => {
    setDraft(EMPTY_FILTERS);
    writeSearch({ filters: EMPTY_FILTERS, page: 0 });
  };

  const goToPage = (next: number) => {
    writeSearch({ page: next });
  };

  const changeSort = (value: string) => {
    writeSearch({ sort: value, page: 0 });
  };

  if (Number.isNaN(reportId)) {
    return <ErrorBanner message="Invalid report id" />;
  }

  const isIrrelevant = appliedFilters.relevance === "IRRELEVANT";
  const sortOptions = isIrrelevant
    ? BASE_SORT_OPTIONS
    : [...BASE_SORT_OPTIONS, ...RELEVANT_SORT_OPTIONS];

  return (
    <div className="posts-list">
      <Link to={`/reports/${reportId}`} className="posts-list-back">
        <ArrowLeft size={16} />
        Back to report
      </Link>

      <div className="posts-list-header">
        <h1 className="posts-list-title">Analyzed posts</h1>
        {data && (
          <p className="posts-list-subtitle">
            {data.totalElements}{" "}
            {data.totalElements === 1 ? "post" : "posts"} match the current
            filters
          </p>
        )}
      </div>

      <form className="posts-list-filters" onSubmit={applyFilters}>
        <div className="posts-list-filter-grid">
          <label className="posts-list-field">
            <span>Relevance</span>
            <select
              value={draft.relevance}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  relevance: e.target.value as RelevanceStatus,
                })
              }
            >
              <option value="RELEVANT">Relevant</option>
              <option value="IRRELEVANT">Filtered out</option>
            </select>
          </label>

          <label className="posts-list-field">
            <span>Language</span>
            <select
              value={draft.language}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  language: e.target.value as LanguageEnum | "",
                })
              }
            >
              <option value="">Any</option>
              {LANGUAGE_OPTIONS.map((lang) => (
                <option key={lang} value={lang}>
                  {lang}
                </option>
              ))}
            </select>
          </label>

          <label className="posts-list-field">
            <span>Date from</span>
            <input
              type="date"
              value={draft.dateFrom}
              onChange={(e) => setDraft({ ...draft, dateFrom: e.target.value })}
            />
          </label>

          <label className="posts-list-field">
            <span>Date to</span>
            <input
              type="date"
              value={draft.dateTo}
              onChange={(e) => setDraft({ ...draft, dateTo: e.target.value })}
            />
          </label>

          {!isIrrelevant && (
            <>
              <label className="posts-list-field">
                <span>Emotion</span>
                <select
                  value={draft.emotion}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      emotion: e.target.value as EmotionEnum | "",
                    })
                  }
                >
                  <option value="">Any</option>
                  {EMOTION_OPTIONS.map((e) => (
                    <option key={e} value={e}>
                      {toTitle(e)}
                    </option>
                  ))}
                </select>
              </label>

              <label className="posts-list-field">
                <span>Aspect</span>
                <select
                  value={draft.aspect}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      aspect: e.target.value as AspectEnum | "",
                    })
                  }
                >
                  <option value="">Any</option>
                  {ASPECT_OPTIONS.map((a) => (
                    <option key={a} value={a}>
                      {toTitle(a)}
                    </option>
                  ))}
                </select>
              </label>

              <label className="posts-list-field posts-list-field--range">
                <span>Sentiment</span>
                <div className="posts-list-range">
                  <input
                    type="number"
                    min="0"
                    max="5"
                    step="0.1"
                    placeholder="min"
                    value={draft.sentimentMin}
                    onChange={(e) =>
                      setDraft({ ...draft, sentimentMin: e.target.value })
                    }
                  />
                  <span className="posts-list-range-sep">–</span>
                  <input
                    type="number"
                    min="0"
                    max="5"
                    step="0.1"
                    placeholder="max"
                    value={draft.sentimentMax}
                    onChange={(e) =>
                      setDraft({ ...draft, sentimentMax: e.target.value })
                    }
                  />
                </div>
              </label>

              <label className="posts-list-field posts-list-field--range">
                <span>Confidence</span>
                <div className="posts-list-range">
                  <input
                    type="number"
                    min="0"
                    max="1"
                    step="0.05"
                    placeholder="min"
                    value={draft.confidenceMin}
                    onChange={(e) =>
                      setDraft({ ...draft, confidenceMin: e.target.value })
                    }
                  />
                  <span className="posts-list-range-sep">–</span>
                  <input
                    type="number"
                    min="0"
                    max="1"
                    step="0.05"
                    placeholder="max"
                    value={draft.confidenceMax}
                    onChange={(e) =>
                      setDraft({ ...draft, confidenceMax: e.target.value })
                    }
                  />
                </div>
              </label>
            </>
          )}
        </div>

        <div className="posts-list-filter-actions">
          <button type="submit" className="posts-list-primary">
            Apply filters
          </button>
          <button
            type="button"
            className="posts-list-secondary"
            onClick={clearFilters}
          >
            Clear
          </button>
          <div className="posts-list-sort">
            <label htmlFor="posts-list-sort-select">Sort</label>
            <select
              id="posts-list-sort-select"
              value={sort}
              onChange={(e) => changeSort(e.target.value)}
            >
              {sortOptions.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </form>

      {error && <ErrorBanner message={error} onRetry={load} />}

      {loading && !data && <p className="posts-list-status">Loading posts…</p>}

      {data && data.content.length === 0 && !loading && (
        <p className="posts-list-status">
          No posts match the current filters.
        </p>
      )}

      {data && data.content.length > 0 && (
        <div className="posts-list-table-wrap">
          <table className="posts-list-table">
            <thead>
              <tr>
                <th>Post</th>
                {isIrrelevant ? (
                  <>
                    <th>Reason</th>
                    <th>Language</th>
                    <th>Source</th>
                    <th>Collected</th>
                  </>
                ) : (
                  <>
                    <th>Score</th>
                    <th>Emotion</th>
                    <th>Aspect</th>
                    <th>Confidence</th>
                    <th>Language</th>
                    <th>Collected</th>
                  </>
                )}
              </tr>
            </thead>
            <tbody>
              {data.content.map((post) => (
                <tr key={post.postId}>
                  <td>
                    <button
                      type="button"
                      className="posts-list-text-trigger"
                      onClick={() => setSelected(post)}
                      title="View full post"
                    >
                      <span className="posts-list-text">
                        {post.postText || "(empty)"}
                      </span>
                    </button>
                  </td>
                  {isIrrelevant ? (
                    <>
                      <td>
                        <span className="posts-list-reason">
                          {post.irrelevanceReason
                            ? REASON_LABEL[post.irrelevanceReason]
                            : "—"}
                        </span>
                      </td>
                      <td>{post.language}</td>
                      <td>
                        {post.postUrl ? (
                          <a
                            href={post.postUrl}
                            target="_blank"
                            rel="noreferrer"
                          >
                            View
                          </a>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td>{formatDate(post.createdAt)}</td>
                    </>
                  ) : (
                    <>
                      <td className="posts-list-numeric">
                        {fmtScore(post.score)}
                      </td>
                      <td>
                        {post.emotion ? (
                          <Badge variant={EMOTION_BADGE_VARIANT[post.emotion]}>
                            {toTitle(post.emotion)}
                          </Badge>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td>
                        {post.aspect ? (
                          <Badge variant="default">{toTitle(post.aspect)}</Badge>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td className="posts-list-numeric">
                        {fmtPct(post.confidence)}
                      </td>
                      <td>{post.language}</td>
                      <td>{formatDate(post.createdAt)}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="posts-list-pagination">
          <button
            type="button"
            onClick={() => goToPage(pageNumber - 1)}
            disabled={pageNumber === 0 || loading}
          >
            Previous
          </button>
          <span>
            Page {pageNumber + 1} of {data.totalPages}
          </span>
          <button
            type="button"
            onClick={() => goToPage(pageNumber + 1)}
            disabled={pageNumber + 1 >= data.totalPages || loading}
          >
            Next
          </button>
        </div>
      )}

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title="Post details"
        width="md"
      >
        {selected && (
          <div className="posts-list-modal">
            <dl className="posts-list-modal-meta">
              {selected.relevanceStatus === "RELEVANT" ? (
                <>
                  <div>
                    <dt>Sentiment</dt>
                    <dd>{fmtScore(selected.score)} / 5</dd>
                  </div>
                  <div>
                    <dt>Confidence</dt>
                    <dd>{fmtPct(selected.confidence)}</dd>
                  </div>
                  <div>
                    <dt>Emotion</dt>
                    <dd>
                      {selected.emotion ? toTitle(selected.emotion) : "—"}
                    </dd>
                  </div>
                  <div>
                    <dt>Aspect</dt>
                    <dd>{selected.aspect ? toTitle(selected.aspect) : "—"}</dd>
                  </div>
                </>
              ) : (
                <div>
                  <dt>Reason</dt>
                  <dd>
                    {selected.irrelevanceReason
                      ? REASON_LABEL[selected.irrelevanceReason]
                      : "—"}
                  </dd>
                </div>
              )}
              <div>
                <dt>Language</dt>
                <dd>{selected.language}</dd>
              </div>
              <div>
                <dt>Collected</dt>
                <dd>{formatDate(selected.createdAt)}</dd>
              </div>
              {selected.postUrl && (
                <div>
                  <dt>Source</dt>
                  <dd>
                    <a
                      href={selected.postUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {selected.postUrl}
                    </a>
                  </dd>
                </div>
              )}
            </dl>
            <p className="posts-list-modal-body">
              {selected.postText || "(empty)"}
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PostsList;
