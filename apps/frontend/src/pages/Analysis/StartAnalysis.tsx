import { useState, useEffect, useCallback, useRef } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import { ArrowLeft, ClipboardPaste, Download, Settings2, Upload } from "lucide-react";
import { getBrands, getBrand } from "../../services/brandService";
import { startAnalysis } from "../../services/reportService";
import {
  downloadDatasetTemplate,
  startAnalysisFromCsv,
  startAnalysisFromPaste,
} from "../../services/datasetService";
import { DatasetValidationFailed } from "../../types/dataset";
import type { DatasetValidationError, PastePreview, PasteColumnMapping } from "../../types/dataset";
import {
  KEYWORD_TYPE_LABELS,
  type BrandSummaryResponse,
  type KeywordInfo,
} from "../../types/brand";
import type { DataSource } from "../../types/dashboard";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { useBrandSelection } from "../../context/useBrandSelection";
import "./StartAnalysis.css";

interface LocationState {
  preselectedBrandId?: number;
}

const MAX_FILE_BYTES = 10 * 1024 * 1024;

const TEXT_CANDIDATES = ["text", "post", "post_text", "content", "body", "message", "tweet"];
const URL_CANDIDATES = ["url", "link", "href", "source"];
const LANG_CANDIDATES = ["language", "lang"];

function detectDelimiter(text: string): "\t" | "," {
  const firstLine = text.split("\n")[0] ?? "";
  const tabs = (firstLine.match(/\t/g) ?? []).length;
  const commas = (firstLine.match(/,/g) ?? []).length;
  return tabs >= commas ? "\t" : ",";
}

function parsePasteText(raw: string): PastePreview {
  const delimiter = detectDelimiter(raw);
  const lines = raw.split("\n").map((l) => l.replace(/\r$/, "")).filter((l) => l.trim());
  if (lines.length === 0) {
    return { rawText: raw, headers: [], previewRows: [], mapping: { text: null, url: null, language: null } };
  }
  const headers = lines[0].split(delimiter).map((h) => h.trim());
  const dataRows = lines.slice(1).map((l) => l.split(delimiter).map((c) => c.trim()));
  const find = (candidates: string[]) =>
    headers.find((h) => candidates.includes(h.toLowerCase())) ?? null;
  const mapping: PasteColumnMapping = {
    text: find(TEXT_CANDIDATES),
    url: find(URL_CANDIDATES),
    language: find(LANG_CANDIDATES),
  };
  return { rawText: raw, headers, previewRows: dataRows.slice(0, 5), mapping };
}

const StartAnalysis = (): React.JSX.Element => {
  const navigate = useNavigate();
  const location = useLocation();
  const { selectedBrandId: navbarBrandId, setSelectedBrandId: setNavbarBrandId } =
    useBrandSelection();
  const preselectedBrandId = (location.state as LocationState | null)
    ?.preselectedBrandId;

  const [brands, setBrands] = useState<BrandSummaryResponse[]>([]);
  const [loadingBrands, setLoadingBrands] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedBrandId, setSelectedBrandId] = useState<number | "">(
    preselectedBrandId ?? navbarBrandId ?? ""
  );
  const [dataSource, setDataSource] = useState<DataSource>("REDDIT");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [file, setFile] = useState<File | null>(null);
  const [csvErrors, setCsvErrors] = useState<DatasetValidationError[]>([]);
  const [templateDownloading, setTemplateDownloading] = useState(false);
  const [templateError, setTemplateError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [datasetTab, setDatasetTab] = useState<"upload" | "paste">("upload");
  const [pastePreview, setPastePreview] = useState<PastePreview | null>(null);
  const pasteAreaRef = useRef<HTMLTextAreaElement | null>(null);
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

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null;
    setSubmitError(null);
    setCsvErrors([]);
    if (!picked) {
      setFile(null);
      return;
    }
    const name = picked.name.toLowerCase();
    if (!name.endsWith(".csv")) {
      setSubmitError("Only .csv files are supported");
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    if (picked.size > MAX_FILE_BYTES) {
      setSubmitError("File too large (max 10 MB)");
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
      return;
    }
    setFile(picked);
  };

  const handleDownloadTemplate = async () => {
    setTemplateDownloading(true);
    setTemplateError(null);
    try {
      await downloadDatasetTemplate();
    } catch (err) {
      setTemplateError(
        err instanceof Error ? err.message : "Failed to download template"
      );
    } finally {
      setTemplateDownloading(false);
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const text = e.clipboardData.getData("text");
    if (!text.trim()) return;
    e.preventDefault();
    setSubmitError(null);
    setCsvErrors([]);
    setPastePreview(parsePasteText(text));
  };

  const handlePasteAreaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const text = e.target.value;
    if (!text.trim()) {
      setPastePreview(null);
      return;
    }
    setPastePreview(parsePasteText(text));
  };

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
    if (dataSource === "CSV_UPLOAD") {
      if (datasetTab === "upload" && !file) {
        setSubmitError("Select a CSV file to upload");
        return;
      }
      if (datasetTab === "paste") {
        if (!pastePreview || !pastePreview.rawText.trim()) {
          setSubmitError("Paste your data first");
          return;
        }
        if (!pastePreview.mapping.text) {
          setSubmitError("Map a column to 'Text' before submitting");
          return;
        }
      }
    }
    if (selectedKeywordIds.size === 0) return;
    setSubmitting(true);
    setSubmitError(null);
    setCsvErrors([]);
    try {
      let report;
      if (dataSource === "CSV_UPLOAD" && datasetTab === "paste" && pastePreview) {
        report = await startAnalysisFromPaste(Number(selectedBrandId), {
          rawText: pastePreview.rawText,
          textColumn: pastePreview.mapping.text as string,
          urlColumn: pastePreview.mapping.url ?? undefined,
          languageColumn: pastePreview.mapping.language ?? undefined,
          dateFrom: dateFrom || undefined,
          dateTo: dateTo || undefined,
        });
      } else if (dataSource === "CSV_UPLOAD") {
        report = await startAnalysisFromCsv(Number(selectedBrandId), {
          file: file as File,
          dateFrom: dateFrom || undefined,
          dateTo: dateTo || undefined,
        });
      } else {
        report = await startAnalysis(Number(selectedBrandId), {
          dataSource,
          dateFrom: dateFrom || undefined,
          dateTo: dateTo || undefined,
          selectedKeywordIds: Array.from(selectedKeywordIds),
        });
      }
      navigate(`/reports/${report.reportId}`, { state: { report } });
    } catch (err) {
      if (err instanceof DatasetValidationFailed) {
        setCsvErrors(err.errors);
        setFile(null);
        if (fileInputRef.current) fileInputRef.current.value = "";
      } else {
        setSubmitError(
          err instanceof Error ? err.message : "Failed to start analysis"
        );
      }
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
                onChange={(e) => {
                  const next =
                    e.target.value === "" ? "" : Number(e.target.value);
                  setSelectedBrandId(next);
                  if (next !== "") setNavbarBrandId(next);
                }}
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
                            {KEYWORD_TYPE_LABELS[kw.keywordType]}
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
                onChange={() => {
                  setDataSource("REDDIT");
                  setCsvErrors([]);
                }}
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
                <span className="start-analysis-radio-title">
                  Upload Custom Dataset
                </span>
                <span className="start-analysis-radio-desc">
                  Upload a CSV of posts you've collected yourself.
                </span>
              </div>
            </label>

            {dataSource === "CSV_UPLOAD" && (
              <div className="start-analysis-csv">
                <div className="start-analysis-dataset-tabs">
                  <button
                    type="button"
                    className={`start-analysis-dataset-tab${datasetTab === "upload" ? " start-analysis-dataset-tab--active" : ""}`}
                    onClick={() => { setDatasetTab("upload"); setCsvErrors([]); setSubmitError(null); }}
                  >
                    <Upload size={13} />
                    Upload file
                  </button>
                  <button
                    type="button"
                    className={`start-analysis-dataset-tab${datasetTab === "paste" ? " start-analysis-dataset-tab--active" : ""}`}
                    onClick={() => { setDatasetTab("paste"); setCsvErrors([]); setSubmitError(null); }}
                  >
                    <ClipboardPaste size={13} />
                    Paste data
                  </button>
                </div>

                {datasetTab === "upload" && (
                  <>
                    <div className="start-analysis-csv-template">
                      <button
                        type="button"
                        className="start-analysis-template-btn"
                        onClick={handleDownloadTemplate}
                        disabled={templateDownloading}
                      >
                        <Download size={14} />
                        {templateDownloading ? "Preparing…" : "Download template"}
                      </button>
                      <p className="start-analysis-hint">
                        Columns: <code>text</code> (required),{" "}
                        <code>url</code> (optional),{" "}
                        <code>language</code> (EN or AR, defaults to EN). Max 10 MB.
                      </p>
                      {templateError && (
                        <p className="start-analysis-hint start-analysis-hint--error">
                          {templateError}
                        </p>
                      )}
                    </div>

                    <label className="start-analysis-field">
                      <span className="start-analysis-label">CSV file</span>
                      <div className="start-analysis-file">
                        <label className="start-analysis-file-btn">
                          <Upload size={14} />
                          {file ? "Choose another file" : "Choose file"}
                          <input
                            ref={fileInputRef}
                            type="file"
                            accept=".csv,text/csv"
                            onChange={handleFileChange}
                            className="start-analysis-file-input"
                          />
                        </label>
                        <span className="start-analysis-file-name">
                          {file ? file.name : "No file selected"}
                        </span>
                      </div>
                    </label>
                  </>
                )}

                {datasetTab === "paste" && (
                  <>
                    {!pastePreview ? (
                      <div className="start-analysis-paste-zone">
                        <ClipboardPaste size={24} className="start-analysis-paste-icon" />
                        <p className="start-analysis-paste-hint">
                          Copy rows from Excel, Google Sheets, or a CSV file, then paste here
                        </p>
                        <textarea
                          ref={pasteAreaRef}
                          className="start-analysis-paste-area"
                          placeholder="Paste your data here (Cmd+V / Ctrl+V)…"
                          rows={6}
                          onPaste={handlePaste}
                          onChange={handlePasteAreaChange}
                          aria-label="Paste dataset"
                        />
                      </div>
                    ) : (
                      <div className="start-analysis-paste-preview">
                        <div className="start-analysis-paste-preview-header">
                          <span className="start-analysis-paste-preview-title">
                            {pastePreview.headers.length} column{pastePreview.headers.length !== 1 ? "s" : ""} detected
                            {" · "}map each to a field
                          </span>
                          <button
                            type="button"
                            className="start-analysis-link-btn"
                            onClick={() => { setPastePreview(null); setCsvErrors([]); }}
                          >
                            Clear
                          </button>
                        </div>

                        <div className="start-analysis-paste-mapping">
                          {(["text", "url", "language"] as const).map((field) => (
                            <label key={field} className="start-analysis-paste-mapping-row">
                              <span className="start-analysis-paste-mapping-field">
                                {field}
                                {field === "text" && <span className="start-analysis-paste-required">*</span>}
                              </span>
                              <select
                                className="start-analysis-input start-analysis-paste-mapping-select"
                                value={pastePreview.mapping[field] ?? ""}
                                onChange={(e) => {
                                  const val = e.target.value || null;
                                  setPastePreview((prev) =>
                                    prev ? { ...prev, mapping: { ...prev.mapping, [field]: val } } : prev
                                  );
                                }}
                              >
                                <option value="">— ignored —</option>
                                {pastePreview.headers.map((h) => (
                                  <option key={h} value={h}>{h}</option>
                                ))}
                              </select>
                            </label>
                          ))}
                        </div>

                        {pastePreview.previewRows.length > 0 && (
                          <div className="start-analysis-paste-table-wrap">
                            <table className="start-analysis-paste-table">
                              <thead>
                                <tr>
                                  {pastePreview.headers.map((h) => (
                                    <th key={h}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {pastePreview.previewRows.map((row, i) => (
                                  <tr key={i}>
                                    {pastePreview.headers.map((_, j) => (
                                      <td key={j}>{row[j] ?? ""}</td>
                                    ))}
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                            <p className="start-analysis-hint">
                              Showing first {pastePreview.previewRows.length} rows
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </>
                )}

                {csvErrors.length > 0 && (
                  <div
                    className="start-analysis-csv-errors"
                    role="alert"
                    aria-live="polite"
                  >
                    <p className="start-analysis-csv-errors-title">
                      {datasetTab === "paste"
                        ? "The pasted data couldn't be processed. Fix these issues:"
                        : "The uploaded file couldn't be processed. Fix these issues and select the file again:"}
                    </p>
                    <ul className="start-analysis-csv-errors-list">
                      {csvErrors.map((err, i) => (
                        <li key={i}>
                          {err.field && (
                            <code className="start-analysis-csv-errors-field">
                              {err.field}
                            </code>
                          )}
                          <span>{err.message}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
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
                !hasSelection ||
                (dataSource === "CSV_UPLOAD" && datasetTab === "upload" && !file) ||
                (dataSource === "CSV_UPLOAD" && datasetTab === "paste" && (!pastePreview || !pastePreview.mapping.text))
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
