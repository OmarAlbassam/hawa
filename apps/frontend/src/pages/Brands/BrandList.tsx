import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { getBrands } from "../../services/brandService";
import type { Page } from "../../types/page";
import type { BrandSummaryResponse } from "../../types/brand";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { formatDate } from "../../utils/formatDate";
import "./BrandList.css";

const BrandList = (): React.JSX.Element => {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<BrandSummaryResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getBrands(page));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  return (
    <div className="brand-list">
      <h1 className="brand-list-title">Brands</h1>

      {error && <ErrorBanner message={error} onRetry={loadData} />}

      {loading ? (
        <div className="brand-list-loading">Loading brands...</div>
      ) : data && data.content.length === 0 ? (
        <div className="brand-list-empty">
          <p>No brands found.</p>
        </div>
      ) : data && (
        <>
          <div className="brand-list-grid">
            {data.content.map((brand) => (
              <Link
                key={brand.brandId}
                to={`/brands/${brand.brandId}`}
                className="brand-card"
              >
                <div className="brand-card-header">
                  <h3 className="brand-card-name">{brand.brandName}</h3>
                  {brand.statusIndicator != null && (
                    <span className="brand-card-score">
                      {brand.statusIndicator.toFixed(1)}
                    </span>
                  )}
                </div>
                {brand.industry && (
                  <span className="brand-card-industry">{brand.industry}</span>
                )}
                <div className="brand-card-meta">
                  <span>{brand.keywordCount} keyword{brand.keywordCount !== 1 ? "s" : ""}</span>
                  <span>{formatDate(brand.createdAt)}</span>
                </div>
              </Link>
            ))}
          </div>

          <div className="brand-list-pagination">
            <button
              className="brand-list-page-btn"
              disabled={data.number === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </button>
            <span className="brand-list-page-info">
              Page {data.number + 1} of {data.totalPages}
            </span>
            <button
              className="brand-list-page-btn"
              disabled={data.number + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
};

export default BrandList;
