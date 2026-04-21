import { useState, useEffect, useCallback } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import { ArrowLeft, Settings2 } from "lucide-react";
import { getBrands, getBrand } from "../../services/brandService";
import { startAnalysis } from "../../services/reportService";
import type { BrandSummaryResponse, KeywordInfo } from "../../types/brand";
import type { DataSource } from "../../types/dashboard";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import "./StartAnalysis.css";

interface LocationState {
  preselectedBrandId?: number;
}

const StartAnalysis = (): React.JSX.Element => {
  const navigate = useNavigate();
  const location = useLocation();
  const preselectedBrandId = (location.state as LocationState | null)
    ?.preselectedBrandId;

  const [brands, setBrands] = useState<BrandSummaryResponse[]>([]);
  const [loadingBrands, setLoadingBrands] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedBrandId, setSelectedBrandId] = useState<number | "">(
    preselectedBrandId ?? ""
  );
  const [dataSource, setDataSource] = useState<DataSource>("REDDIT");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [brandKeywords, setBrandKeywords] = useState<KeywordInfo[]>([]);
  const [loadingKeywords, setLoadingKeywords] = useState(false);
  const [keywordError, setKeywordError] = useState<string | null>(null);
  const [selectedKeywordIds, setSelectedKeywordIds] = useState<Set<number>>(
    new Set()
  );

  const loadBrands = useCallback(async () => {
    setLoadingBrands(true);
    setLoadError(null);
    try {
      const page = await getBrands(0, 100);
      setBrands(page.content);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoadingBrands(false);
    }
  }, []);

  useEffect(() => {
    loadBrands();
  }, [loadBrands]);

  useEffect(() => {
    if (!selectedBrandId) {
      setBrandKeywords([]);
      setSelectedKeywordIds(new Set());
      setKeywordError(null);
      return;
    }
    let cancelled = false;
    setLoadingKeywords(true);
    setKeywordError(null);
    getBrand(Number(selectedBrandId))
      .then((detail) => {
        if (cancelled) return;
        setBrandKeywords(detail.keywords);
        setSelectedKeywordIds(new Set(detail.keywords.map((k) => k.keywordId)));
      })
      .catch((err) => {
        if (cancelled) return;
        setKeywordError(
          err instanceof Error ? err.message : "Failed to load keywords"
        );
        setBrandKeywords([]);
        setSelectedKeywordIds(new Set());
      })
      .finally(() => {
        if (!cancelled) setLoadingKeywords(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedBrandId]);

  const validDateRange =
    !dateFrom || !dateTo || new Date(dateFrom) <= new Date(dateTo);

  const backTarget = preselectedBrandId
    ? `/brands/${preselectedBrandId}`
    : "/dashboard";
  const backLabel = preselectedBrandId ? "Back to brand" : "Back to dashboard";

  const toggleKeyword = (keywordId: number) => {
    setSelectedKeywordIds((prev) => {
      const next = new Set(prev);
      if (next.has(keywordId)) {
        next.delete(keywordId);
      } else {
        next.add(keywordId);
      }
      return next;
    });
  };

  const selectAllKeywords = () => {
    setSelectedKeywordIds(new Set(brandKeywords.map((k) => k.keywordId)));
  };

  const clearKeywords = () => {
    setSelectedKeywordIds(new Set());
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedBrandId || !validDateRange) return;
    if (selectedKeywordIds.size === 0) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const report = await startAnalysis(Number(selectedBrandId), {
        dataSource,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        selectedKeywordIds: Array.from(selectedKeywordIds),
      });
      navigate(`/reports/${report.reportId}`, { state: { report } });
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : "Failed to start analysis"
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (loadingBrands) {
    return <div className="start-analysis-loading">Loading brands...</div>;
  }

  if (loadError) {
    return <ErrorBanner message={loadError} onRetry={loadBrands} />;
  }

  const selectedBrand = brands.find((b) => b.brandId === selectedBrandId);
  const hasSelection = selectedKeywordIds.size > 0;

  return (
    <div className="start-analysis">
      <Link to={backTarget} className="start-analysis-back">
        <ArrowLeft size={16} />
        {backLabel}
      </Link>

      <div className="start-analysis-header">
        <h1 className="start-analysis-title">Start Analysis</h1>
        <p className="start-analysis-subtitle">
          {selectedBrand ? (
            <>
              Run a sentiment analysis for{" "}
              <strong>{selectedBrand.brandName}</strong>. Pick the keywords to
              use for this run.
            </>
          ) : (
            "Select a brand and configure the analysis below."
          )}
        </p>
      </div>

      {brands.length === 0 ? (
        <div className="start-analysis-empty">
          <p>You don't have any brands yet.</p>
          <Link to="/brands" className="start-analysis-submit">
            Go to Brands
          </Link>
        </div>
      ) : (
        <form className="start-analysis-form" onSubmit={handleSubmit}>
          <fieldset className="start-analysis-fieldset">
            <legend className="start-analysis-legend">Brand</legend>
            <label className="start-analysis-field">
              <span className="start-analysis-label">Select a brand</span>
              <select
                className="start-analysis-input"
                value={selectedBrandId}
                onChange={(e) =>
                  setSelectedBrandId(
                    e.target.value === "" ? "" : Number(e.target.value)
                  )
                }
                required
              >
                <option value="" disabled>
                  Choose a brand…
                </option>
                {brands.map((b) => (
                  <option key={b.brandId} value={b.brandId}>
                    {b.brandName}
                    {b.industry ? ` — ${b.industry}` : ""}
                  </option>
                ))}
              </select>
            </label>
            {selectedBrand && (
              <Link
                to={`/brands/${selectedBrand.brandId}`}
                className="start-analysis-keywords-btn"
              >
                <Settings2 size={14} />
                Configure keywords for {selectedBrand.brandName}
              </Link>
            )}
          </fieldset>

          {selectedBrandId !== "" && (
            <fieldset className="start-analysis-fieldset">
              <legend className="start-analysis-legend">Keywords</legend>
              {loadingKeywords ? (
                <p className="start-analysis-hint">Loading keywords…</p>
              ) : keywordError ? (
                <p className="start-analysis-hint start-analysis-hint--error">
                  {keywordError}
                </p>
              ) : brandKeywords.length === 0 ? (
                <p className="start-analysis-hint start-analysis-hint--error">
                  This brand has no keywords. Add at least one on the brand
                  page before starting an analysis.
                </p>
              ) : (
                <>
                  <p className="start-analysis-hint">
                    Only selected keywords will drive post collection and LLM
                    context for this run.
                  </p>
                  <div className="start-analysis-keyword-actions">
                    <button
                      type="button"
                      className="start-analysis-link-btn"
                      onClick={selectAllKeywords}
                    >
                      Select all
                    </button>
                    <button
                      type="button"
                      className="start-analysis-link-btn"
                      onClick={clearKeywords}
                    >
                      Clear
                    </button>
                  </div>
                  <ul className="start-analysis-keyword-list">
                    {brandKeywords.map((kw) => (
                      <li
                        key={kw.keywordId}
                        className="start-analysis-keyword-item"
                      >
                        <label className="start-analysis-keyword-label">
                          <input
                            type="checkbox"
                            checked={selectedKeywordIds.has(kw.keywordId)}
                            onChange={() => toggleKeyword(kw.keywordId)}
                          />
                          <span className="start-analysis-keyword-text">
                            {kw.keyword}
                          </span>
                          <span className="start-analysis-keyword-type">
                            {kw.keywordType}
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                  {!hasSelection && (
                    <p className="start-analysis-hint start-analysis-hint--error">
                      Select at least one keyword.
                    </p>
                  )}
                </>
              )}
            </fieldset>
          )}

          <fieldset className="start-analysis-fieldset">
            <legend className="start-analysis-legend">Data source</legend>
            <label className="start-analysis-radio">
              <input
                type="radio"
                name="dataSource"
                value="REDDIT"
                checked={dataSource === "REDDIT"}
                onChange={() => setDataSource("REDDIT")}
              />
              <div>
                <span className="start-analysis-radio-title">Reddit</span>
                <span className="start-analysis-radio-desc">
                  Collect posts from Reddit using the selected keywords.
                </span>
              </div>
            </label>
            <label className="start-analysis-radio">
              <input
                type="radio"
                name="dataSource"
                value="CSV_UPLOAD"
                checked={dataSource === "CSV_UPLOAD"}
                onChange={() => setDataSource("CSV_UPLOAD")}
              />
              <div>
                <span className="start-analysis-radio-title">CSV upload</span>
                <span className="start-analysis-radio-desc">
                  Use a previously uploaded dataset for this brand.
                </span>
              </div>
            </label>
          </fieldset>

          <fieldset className="start-analysis-fieldset">
            <legend className="start-analysis-legend">
              Date range (optional)
            </legend>
            <div className="start-analysis-date-row">
              <label className="start-analysis-field">
                <span className="start-analysis-label">From</span>
                <input
                  type="date"
                  className="start-analysis-input"
                  value={dateFrom}
                  max={dateTo || undefined}
                  onChange={(e) => setDateFrom(e.target.value)}
                />
              </label>
              <label className="start-analysis-field">
                <span className="start-analysis-label">To</span>
                <input
                  type="date"
                  className="start-analysis-input"
                  value={dateTo}
                  min={dateFrom || undefined}
                  onChange={(e) => setDateTo(e.target.value)}
                />
              </label>
            </div>
            {!validDateRange && (
              <p className="start-analysis-hint start-analysis-hint--error">
                End date must be on or after start date.
              </p>
            )}
          </fieldset>

          {submitError && <ErrorBanner message={submitError} />}

          <div className="start-analysis-actions">
            <Link to={backTarget} className="start-analysis-cancel">
              Cancel
            </Link>
            <button
              type="submit"
              className="start-analysis-submit"
              disabled={
                submitting ||
                !validDateRange ||
                !selectedBrandId ||
                !hasSelection
              }
            >
              {submitting ? "Starting..." : "Start analysis"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
};

export default StartAnalysis;
