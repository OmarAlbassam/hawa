import { useState, useEffect, useCallback } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { getBrand } from "../../services/brandService";
import type { BrandDetailResponse } from "../../types/brand";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import KeywordPanel from "./KeywordPanel";
import { useBrandSelection } from "../../context/useBrandSelection";
import { formatDate } from "../../utils/formatDate";
import "./BrandDetail.css";

const BrandDetail = (): React.JSX.Element => {
  const { brandId } = useParams<{ brandId: string }>();
  const navigate = useNavigate();
  const { selectedBrandId, setSelectedBrandId } = useBrandSelection();
  const [brand, setBrand] = useState<BrandDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const urlBrandId = brandId ? Number(brandId) : null;

  // Keep navbar selection in sync with the URL when the route changes.
  useEffect(() => {
    if (urlBrandId != null && urlBrandId !== selectedBrandId) {
      setSelectedBrandId(urlBrandId);
    }
  }, [urlBrandId, selectedBrandId, setSelectedBrandId]);

  // Navigate to the matching detail page when the navbar selection changes.
  useEffect(() => {
    if (
      selectedBrandId != null &&
      urlBrandId != null &&
      selectedBrandId !== urlBrandId
    ) {
      navigate(`/brands/${selectedBrandId}`, { replace: true });
    }
  }, [selectedBrandId, urlBrandId, navigate]);

  const loadData = useCallback(async () => {
    if (!brandId) return;
    setLoading(true);
    setError(null);
    try {
      setBrand(await getBrand(Number(brandId)));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }, [brandId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return <div className="brand-detail-loading">Loading brand…</div>;
  }

  if (error) {
    return <ErrorBanner message={error} onRetry={loadData} />;
  }

  if (!brand) return <></>;

  return (
    <div className="brand-detail">
      <Link to="/brands" className="brand-detail-back">
        <ArrowLeft size={16} />
        Back to Brands
      </Link>

      <div className="brand-detail-header">
        <div>
          <h1 className="brand-detail-name">{brand.brandName}</h1>
          {brand.industry && (
            <span className="brand-detail-industry">{brand.industry}</span>
          )}
        </div>
        {brand.statusIndicator != null && (
          <div className="brand-detail-score">
            <span className="brand-detail-score-value">
              {brand.statusIndicator.toFixed(1)}
            </span>
            <span className="brand-detail-score-label">Health Score</span>
          </div>
        )}
      </div>

      <div className="brand-detail-meta">
        <span>Created {formatDate(brand.createdAt)}</span>
        <span>Updated {formatDate(brand.updatedAt)}</span>
      </div>

      <section className="brand-detail-card">
        <h2 className="brand-detail-section-title">Keywords</h2>
        <KeywordPanel brandId={brand.brandId} />
      </section>

      <div className="brand-detail-actions">
        <Link
          to="/analyze"
          state={{ preselectedBrandId: brand.brandId }}
          className="brand-detail-analyze-btn"
        >
          Start Analysis
        </Link>
        <Link to="/reports" className="brand-detail-reports-btn">
          View Reports
        </Link>
      </div>
    </div>
  );
};

export default BrandDetail;
