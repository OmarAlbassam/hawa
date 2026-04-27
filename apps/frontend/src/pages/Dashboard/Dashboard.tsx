import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { FileBarChart, MessageSquare, Tag, Play } from "lucide-react";
import { getDashboard } from "../../services/dashboardService";
import { getBrandStatusIndicator } from "../../services/brandService";
import type { DashboardResponse } from "../../types/dashboard";
import type { StatusIndicatorResponse } from "../../types/statusIndicator";
import StatCard from "../../components/StatCard/StatCard";
import Badge from "../../components/Badge/Badge";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import StatusIndicator from "../../components/StatusIndicator/StatusIndicator";
import { formatDate } from "../../utils/formatDate";
import "./Dashboard.css";

const statusBadgeVariant: Record<string, "default" | "primary" | "success" | "warning" | "error" | "info"> = {
  PENDING: "default",
  PROCESSING: "info",
  COMPLETED: "success",
  FAILED: "error",
};

const Dashboard = (): React.JSX.Element => {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedBrandId, setSelectedBrandId] = useState<number | null>(null);
  const [indicator, setIndicator] = useState<StatusIndicatorResponse | null>(
    null
  );
  const [indicatorLoading, setIndicatorLoading] = useState(false);
  const [indicatorError, setIndicatorError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getDashboard());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (data && selectedBrandId == null && data.brands.length > 0) {
      setSelectedBrandId(data.brands[0].brandId);
    }
  }, [data, selectedBrandId]);

  const loadIndicator = useCallback(async (brandId: number) => {
    setIndicatorLoading(true);
    setIndicatorError(null);
    try {
      setIndicator(await getBrandStatusIndicator(brandId));
    } catch (err) {
      setIndicatorError(
        err instanceof Error ? err.message : "Failed to load status indicator"
      );
    } finally {
      setIndicatorLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedBrandId != null) {
      loadIndicator(selectedBrandId);
    }
  }, [selectedBrandId, loadIndicator]);

  if (loading) {
    return <div className="dashboard-loading">Loading dashboard...</div>;
  }

  if (error) {
    return <ErrorBanner message={error} onRetry={loadData} />;
  }

  if (!data) return <></>;

  return (
    <div className="dashboard">
      <div className="dashboard-header">
        <h1 className="dashboard-title">Dashboard</h1>
        <Link to="/analyze" className="dashboard-start-analysis-btn">
          <Play size={16} />
          Start Analysis
        </Link>
      </div>

      {data.brands.length > 0 && (
        <section className="dashboard-indicator-card">
          <div className="dashboard-indicator-head">
            <h2 className="dashboard-card-title">Brand health</h2>
            <label className="dashboard-indicator-selector">
              <span className="dashboard-indicator-selector-label">Brand</span>
              <select
                className="dashboard-indicator-select"
                value={selectedBrandId ?? ""}
                onChange={(e) => setSelectedBrandId(Number(e.target.value))}
              >
                {data.brands.map((b) => (
                  <option key={b.brandId} value={b.brandId}>
                    {b.brandName}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {indicatorError && (
            <ErrorBanner
              message={indicatorError}
              onRetry={() =>
                selectedBrandId != null && loadIndicator(selectedBrandId)
              }
            />
          )}
          {!indicatorError && indicatorLoading && !indicator && (
            <div className="dashboard-indicator-loading">
              Loading status indicator...
            </div>
          )}
          {!indicatorError && indicator && <StatusIndicator data={indicator} />}
        </section>
      )}

      <div className="dashboard-stats">
        <StatCard label="Total Brands" value={data.stats.totalBrands} icon={Tag} variant="info" />
        <StatCard label="Total Reports" value={data.stats.totalReports} icon={FileBarChart} variant="default" />
        <StatCard label="Posts Analyzed" value={data.stats.totalPostsAnalyzed} icon={MessageSquare} variant="success" />
      </div>

      <div className="dashboard-sections">
        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h2 className="dashboard-card-title">Your Brands</h2>
            <Link to="/brands" className="dashboard-link-btn">
              View all
            </Link>
          </div>
          {data.brands.length === 0 ? (
            <p className="dashboard-empty">No brands yet.</p>
          ) : (
            <div className="dashboard-brands">
              {data.brands.map((brand) => (
                <Link
                  key={brand.brandId}
                  to={`/brands/${brand.brandId}`}
                  className="dashboard-brand-item"
                >
                  <span className="dashboard-brand-name">{brand.brandName}</span>
                  {brand.industry && (
                    <span className="dashboard-brand-industry">{brand.industry}</span>
                  )}
                  {brand.statusIndicator != null && (
                    <span className="dashboard-brand-score">
                      {brand.statusIndicator.toFixed(1)}
                    </span>
                  )}
                </Link>
              ))}
            </div>
          )}
        </section>

        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h2 className="dashboard-card-title">Recent Reports</h2>
            <Link to="/reports" className="dashboard-link-btn">
              View all
            </Link>
          </div>
          {data.recentReports.length === 0 ? (
            <p className="dashboard-empty">No reports yet.</p>
          ) : (
            <table className="dashboard-table">
              <thead>
                <tr>
                  <th>Brand</th>
                  <th>Source</th>
                  <th>Status</th>
                  <th>Score</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {data.recentReports.map((report) => (
                  <tr key={report.reportId}>
                    <td>{report.brandName}</td>
                    <td>{report.dataSource === "CSV_UPLOAD" ? "CSV" : "Reddit"}</td>
                    <td>
                      <Badge variant={statusBadgeVariant[report.status]}>
                        {report.status}
                      </Badge>
                    </td>
                    <td>{report.score != null ? report.score.toFixed(1) : "—"}</td>
                    <td>{formatDate(report.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </div>
  );
};

export default Dashboard;
