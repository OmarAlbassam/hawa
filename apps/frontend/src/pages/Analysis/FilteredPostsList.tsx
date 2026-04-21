import { useCallback, useEffect, useState } from "react";
import { getReportPosts } from "../../services/reportService";
import type {
  IrrelevanceReason,
  PostListItemResponse,
} from "../../types/report";
import type { Page } from "../../types/page";
import Modal from "../../components/Modal/Modal";
import "./FilteredPostsList.css";

const REASON_LABEL: Record<IrrelevanceReason, string> = {
  OFF_TOPIC: "Off-topic",
  SPAM: "Spam",
  EMPTY: "Empty",
  WRONG_LANGUAGE: "Wrong language",
  OTHER: "Other",
};

const PAGE_SIZE = 10;

interface Props {
  reportId: number;
}

const FilteredPostsList = ({ reportId }: Props): React.JSX.Element => {
  const [page, setPage] = useState<Page<PostListItemResponse> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<PostListItemResponse | null>(null);

  const load = useCallback(
    async (targetPage: number) => {
      setLoading(true);
      setError(null);
      try {
        const result = await getReportPosts(reportId, {
          relevance: "IRRELEVANT",
          page: targetPage,
          size: PAGE_SIZE,
        });
        setPage(result);
        setPageNumber(targetPage);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load filtered posts"
        );
      } finally {
        setLoading(false);
      }
    },
    [reportId]
  );

  useEffect(() => {
    void load(0);
  }, [load]);

  if (loading && !page) {
    return <p className="filtered-posts-status">Loading filtered posts...</p>;
  }

  if (error) {
    return (
      <div className="filtered-posts-error">
        <p>{error}</p>
        <button type="button" onClick={() => void load(pageNumber)}>
          Retry
        </button>
      </div>
    );
  }

  if (!page || page.content.length === 0) {
    return <p className="filtered-posts-status">No filtered posts to show.</p>;
  }

  return (
    <div className="filtered-posts">
      <p className="filtered-posts-hint">
        These posts were classified as not about the brand, so they are not
        counted in the sentiment score or distributions.
      </p>
      <table className="filtered-posts-table">
        <thead>
          <tr>
            <th>Post</th>
            <th>Reason</th>
            <th>Language</th>
            <th>Source</th>
          </tr>
        </thead>
        <tbody>
          {page.content.map((post) => (
            <tr key={post.postId}>
              <td>
                <button
                  type="button"
                  className="filtered-posts-text-trigger"
                  onClick={() => setSelected(post)}
                  title="View full post"
                >
                  <span className="filtered-posts-text">
                    {post.postText || "(empty)"}
                  </span>
                </button>
              </td>
              <td>
                <span className="filtered-posts-reason">
                  {post.irrelevanceReason
                    ? REASON_LABEL[post.irrelevanceReason]
                    : "—"}
                </span>
              </td>
              <td>{post.language}</td>
              <td>
                {post.postUrl ? (
                  <a href={post.postUrl} target="_blank" rel="noreferrer">
                    View
                  </a>
                ) : (
                  "—"
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title="Filtered post"
        width="md"
      >
        {selected && (
          <div className="filtered-posts-modal">
            <dl className="filtered-posts-modal-meta">
              <div>
                <dt>Reason</dt>
                <dd>
                  <span className="filtered-posts-reason">
                    {selected.irrelevanceReason
                      ? REASON_LABEL[selected.irrelevanceReason]
                      : "—"}
                  </span>
                </dd>
              </div>
              <div>
                <dt>Language</dt>
                <dd>{selected.language}</dd>
              </div>
              {selected.postUrl && (
                <div>
                  <dt>Source</dt>
                  <dd>
                    <a href={selected.postUrl} target="_blank" rel="noreferrer">
                      {selected.postUrl}
                    </a>
                  </dd>
                </div>
              )}
            </dl>
            <p className="filtered-posts-modal-body">
              {selected.postText || "(empty)"}
            </p>
          </div>
        )}
      </Modal>

      {page.totalPages > 1 && (
        <div className="filtered-posts-pagination">
          <button
            type="button"
            onClick={() => void load(pageNumber - 1)}
            disabled={pageNumber === 0 || loading}
          >
            Previous
          </button>
          <span>
            Page {pageNumber + 1} of {page.totalPages}
          </span>
          <button
            type="button"
            onClick={() => void load(pageNumber + 1)}
            disabled={pageNumber + 1 >= page.totalPages || loading}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
};

export default FilteredPostsList;
