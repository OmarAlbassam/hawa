import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { getBrand } from "../../services/brandService";
import { startAnalysis } from "../../services/reportService";
import type { BrandDetailResponse } from "../../types/brand";
import type { DataSource } from "../../types/dashboard";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import "./StartAnalysis.css";

const StartAnalysis = (): React.JSX.Element => {
  const { brandId } = useParams<{ brandId: string }>();
  const navigate = useNavigate();

  const [brand, setBrand] = useState<BrandDetailResponse | null>(null);
  const [loadingBrand, setLoadingBrand] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [dataSource, setDataSource] = useState<DataSource>("REDDIT");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const loadBrand = useCallback(async () => {
    if (!brandId) return;
    setLoadingBrand(true);
    setLoadError(null);
    try {
      setBrand(await getBrand(Number(brandId)));
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoadingBrand(false);
    }
  }, [brandId]);

  useEffect(() => {
    loadBrand();
  }, [loadBrand]);

  const validDateRange =
    !dateFrom || !dateTo || new Date(dateFrom) <= new Date(dateTo);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!brandId || !validDateRange) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const report = await startAnalysis(Number(brandId), {
        dataSource,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
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

  if (loadingBrand) {
    return <div className="start-analysis-loading">Loading brand...</div>;
  }

  if (loadError) {
    return <ErrorBanner message={loadError} onRetry={loadBrand} />;
  }

  if (!brand) return <></>;

  return (
    <div className="start-analysis">
      <button
        className="start-analysis-back"
        onClick={() => navigate(`/brands/${brand.brandId}`)}
        type="button"
      >
        <ArrowLeft size={16} />
        Back to {brand.brandName}
      </button>

      <div className="start-analysis-header">
        <h1 className="start-analysis-title">Start Analysis</h1>
        <p className="start-analysis-subtitle">
          Run a sentiment analysis for <strong>{brand.brandName}</strong>.
          Keywords configured on the brand are used automatically.
        </p>
      </div>

      <form className="start-analysis-form" onSubmit={handleSubmit}>
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
              <span className="start-analysis-radio-title">CSV upload</span>
              <span className="start-analysis-radio-desc">
                Use a previously uploaded dataset for this brand.
              </span>
            </div>
          </label>
        </fieldset>

        <fieldset className="start-analysis-fieldset">
          <legend className="start-analysis-legend">Date range (optional)</legend>
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
          <button
            type="button"
            className="start-analysis-cancel"
            onClick={() => navigate(`/brands/${brand.brandId}`)}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="start-analysis-submit"
            disabled={submitting || !validDateRange}
          >
            {submitting ? "Starting..." : "Start analysis"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default StartAnalysis;
