import { useState, useEffect, useCallback, useRef } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import { ArrowLeft, Download, Settings2, Upload } from "lucide-react";
import { getBrands } from "../../services/brandService";
import { startAnalysis } from "../../services/reportService";
import {
  downloadDatasetTemplate,
  startAnalysisFromCsv,
} from "../../services/datasetService";
import { DatasetValidationFailed } from "../../types/dataset";
import type { DatasetValidationError } from "../../types/dataset";
import type { BrandSummaryResponse } from "../../types/brand";
import type { DataSource } from "../../types/dashboard";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import "./StartAnalysis.css";

interface LocationState {
  preselectedBrandId?: number;
}

const MAX_FILE_BYTES = 10 * 1024 * 1024;

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

  const [file, setFile] = useState<File | null>(null);
  const [csvErrors, setCsvErrors] = useState<DatasetValidationError[]>([]);
  const [templateDownloading, setTemplateDownloading] = useState(false);
  const [templateError, setTemplateError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedBrandId || !validDateRange) return;
    if (dataSource === "CSV_UPLOAD" && !file) {
      setSubmitError("Select a CSV file to upload");
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    setCsvErrors([]);
    try {
      const report =
        dataSource === "CSV_UPLOAD"
          ? await startAnalysisFromCsv(Number(selectedBrandId), {
              file: file as File,
              dateFrom: dateFrom || undefined,
              dateTo: dateTo || undefined,
            })
          : await startAnalysis(Number(selectedBrandId), {
              dataSource,
              dateFrom: dateFrom || undefined,
              dateTo: dateTo || undefined,
            });
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
              <strong>{selectedBrand.brandName}</strong>. Keywords configured on
              the brand are used automatically.
            </>
          ) : (
            "Select a brand and configure the analysis below. Keywords configured on the brand are used automatically."
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
                  Collect posts from Reddit using the brand's keywords.
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
                <div className="start-analysis-csv-template">
                  <button
                    type="button"
                    className="start-analysis-template-btn"
                    onClick={handleDownloadTemplate}
                    disabled={templateDownloading}
                  >
                    <Download size={14} />
                    {templateDownloading
                      ? "Preparing…"
                      : "Download template"}
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

                {csvErrors.length > 0 && (
                  <div
                    className="start-analysis-csv-errors"
                    role="alert"
                    aria-live="polite"
                  >
                    <p className="start-analysis-csv-errors-title">
                      The uploaded file couldn't be processed. Fix these issues
                      and select the file again:
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
                (dataSource === "CSV_UPLOAD" && !file)
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
