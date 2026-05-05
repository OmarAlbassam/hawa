import { useState, useEffect } from "react";
import { Plus, Pencil, Trash2, Check, X, ChevronLeft, ChevronRight } from "lucide-react";
import {
  getKeywords,
  createKeyword,
  updateKeyword,
  deleteKeyword,
} from "../../services/brandService";
import type { Page } from "../../types/page";
import {
  KEYWORD_TYPES,
  KEYWORD_TYPE_LABELS,
  type KeywordInfo,
  type KeywordType,
} from "../../types/brand";
import Badge from "../../components/Badge/Badge";
import ConfirmDialog from "../../components/ConfirmDialog/ConfirmDialog";
import "./KeywordPanel.css";

const TYPE_BADGE_VARIANT: Record<KeywordType, "primary" | "info" | "warning" | "default"> = {
  BRAND_NAME: "primary",
  PRODUCT: "info",
  MISSPELLING: "warning",
  OTHER: "default",
};

interface KeywordPanelProps {
  brandId: number;
}

const KeywordPanel = ({ brandId }: KeywordPanelProps): React.JSX.Element => {
  const [data, setData] = useState<Page<KeywordInfo> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  // Add form
  const [newKeyword, setNewKeyword] = useState("");
  const [newType, setNewType] = useState<KeywordType>("BRAND_NAME");
  const [addLoading, setAddLoading] = useState(false);
  const [addError, setAddError] = useState("");

  // Inline edit
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editKeyword, setEditKeyword] = useState("");
  const [editType, setEditType] = useState<KeywordType>("BRAND_NAME");
  const [editLoading, setEditLoading] = useState(false);

  // Delete
  const [deletingKeyword, setDeletingKeyword] = useState<KeywordInfo | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    getKeywords(brandId, page)
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : "Failed to load keywords");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [brandId, page, refreshKey]);

  const handleAdd = async () => {
    const trimmed = newKeyword.trim();
    if (!trimmed) return;
    setAddLoading(true);
    setAddError("");
    try {
      await createKeyword(brandId, { keyword: trimmed, keywordType: newType });
      setNewKeyword("");
      setNewType("BRAND_NAME");
      setPage(0);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setAddError(err instanceof Error ? err.message : "Failed to add keyword");
    } finally {
      setAddLoading(false);
    }
  };

  const handleEditStart = (kw: KeywordInfo) => {
    setEditingId(kw.keywordId);
    setEditKeyword(kw.keyword);
    setEditType(kw.keywordType);
  };

  const handleEditSave = async () => {
    if (editingId === null) return;
    const trimmed = editKeyword.trim();
    if (!trimmed) return;
    setEditLoading(true);
    try {
      await updateKeyword(brandId, editingId, {
        keyword: trimmed,
        keywordType: editType,
      });
      setEditingId(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update keyword");
    } finally {
      setEditLoading(false);
    }
  };

  const handleEditCancel = () => {
    setEditingId(null);
  };

  const handleDelete = async () => {
    if (!deletingKeyword) return;
    setDeleteLoading(true);
    try {
      await deleteKeyword(brandId, deletingKeyword.keywordId);
      setDeletingKeyword(null);
      if (keywords.length === 1 && page > 0) {
        setPage((p) => p - 1);
      } else {
        setRefreshKey((k) => k + 1);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete keyword");
      setDeletingKeyword(null);
    } finally {
      setDeleteLoading(false);
    }
  };

  const keywords = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="keywords-panel">
      <div className="keywords-add-row">
        <input
          className="keywords-add-input"
          type="text"
          placeholder="Enter keyword..."
          value={newKeyword}
          onChange={(e) => setNewKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              handleAdd();
            }
          }}
          disabled={addLoading}
        />
        <select
          className="keywords-add-select"
          value={newType}
          onChange={(e) => setNewType(e.target.value as KeywordType)}
          disabled={addLoading}
        >
          {KEYWORD_TYPES.map((t) => (
            <option key={t} value={t}>
              {KEYWORD_TYPE_LABELS[t]}
            </option>
          ))}
        </select>
        <button
          className="keywords-add-btn"
          onClick={handleAdd}
          disabled={addLoading || !newKeyword.trim()}
        >
          <Plus size={16} />
          {addLoading ? "..." : "Add"}
        </button>
      </div>

      {addError && <div className="keywords-error">{addError}</div>}
      {error && <div className="keywords-error">{error}</div>}

      {loading ? (
        <div className="keywords-loading">Loading...</div>
      ) : keywords.length === 0 ? (
        <div className="keywords-empty">No keywords yet. Add one above.</div>
      ) : (
        <>
          <table className="keywords-table">
            <thead>
              <tr>
                <th>Keyword</th>
                <th>Type</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {keywords.map((kw) =>
                editingId === kw.keywordId ? (
                  <tr key={kw.keywordId}>
                    <td>
                      <input
                        className="keywords-edit-input"
                        type="text"
                        value={editKeyword}
                        onChange={(e) => setEditKeyword(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") {
                            e.preventDefault();
                            handleEditSave();
                          }
                          if (e.key === "Escape") handleEditCancel();
                        }}
                        disabled={editLoading}
                        autoFocus
                      />
                    </td>
                    <td>
                      <select
                        className="keywords-edit-select"
                        value={editType}
                        onChange={(e) => setEditType(e.target.value as KeywordType)}
                        disabled={editLoading}
                      >
                        {KEYWORD_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {KEYWORD_TYPE_LABELS[t]}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <div className="keywords-actions">
                        <button
                          className="keywords-action-btn keywords-action-btn--save"
                          onClick={handleEditSave}
                          disabled={editLoading || !editKeyword.trim()}
                          aria-label="Save"
                        >
                          <Check size={16} />
                        </button>
                        <button
                          className="keywords-action-btn"
                          onClick={handleEditCancel}
                          disabled={editLoading}
                          aria-label="Cancel"
                        >
                          <X size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  <tr key={kw.keywordId}>
                    <td>{kw.keyword}</td>
                    <td>
                      <Badge variant={TYPE_BADGE_VARIANT[kw.keywordType]}>
                        {KEYWORD_TYPE_LABELS[kw.keywordType]}
                      </Badge>
                    </td>
                    <td>
                      <div className="keywords-actions">
                        <button
                          className="keywords-action-btn"
                          onClick={() => handleEditStart(kw)}
                          aria-label="Edit keyword"
                        >
                          <Pencil size={14} />
                        </button>
                        <button
                          className="keywords-action-btn keywords-action-btn--danger"
                          onClick={() => setDeletingKeyword(kw)}
                          aria-label="Delete keyword"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              )}
            </tbody>
          </table>

          {totalPages > 1 && (
            <div className="keywords-pagination">
              <span className="keywords-pagination-info">
                {totalElements} keyword{totalElements !== 1 ? "s" : ""}
              </span>
              <div className="keywords-pagination-controls">
                <button
                  className="keywords-page-btn"
                  onClick={() => setPage((p) => p - 1)}
                  disabled={page === 0}
                  aria-label="Previous page"
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  className="keywords-page-btn"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page >= totalPages - 1}
                  aria-label="Next page"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={deletingKeyword !== null}
        onClose={() => setDeletingKeyword(null)}
        onConfirm={handleDelete}
        title="Delete Keyword"
        message={`Are you sure you want to delete "${deletingKeyword?.keyword}"?`}
        confirmLabel="Delete"
        variant="destructive"
        loading={deleteLoading}
      />
    </div>
  );
};

export default KeywordPanel;
