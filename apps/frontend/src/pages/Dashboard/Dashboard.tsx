import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { FileBarChart, MessageSquare, Tag } from "lucide-react";
import { getDashboard } from "../../services/dashboardService";
import type { DashboardResponse } from "../../types/dashboard";
import StatCard from "../../components/StatCard/StatCard";
import Badge from "../../components/Badge/Badge";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { formatDate } from "../../utils/formatDate";
import "./Dashboard.css";

const statusBadgeVariant: Record<string, "default" | "primary" | "success" | "warning" | "error" | "info"> = {
  PENDING: "default",
  PROCESSING: "info",
  COMPLETED: "success",
  FAILED: "error",
};

const Dashboard = (): React.JSX.Element => {
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  if (loading) {
    return <div className="dashboard-loading">Loading dashboard...</div>;
  }

  if (error) {
    return <ErrorBanner message={error} onRetry={loadData} />;
  }

  if (!data) return <></>;

  return (
    <div className="dashboard">
      <h1 className="dashboard-title">Dashboard</h1>

      <div className="dashboard-stats">
        <StatCard label="Total Brands" value={data.stats.totalBrands} icon={Tag} variant="info" />
        <StatCard label="Total Reports" value={data.stats.totalReports} icon={FileBarChart} variant="default" />
        <StatCard label="Posts Analyzed" value={data.stats.totalPostsAnalyzed} icon={MessageSquare} variant="success" />
      </div>

      <div className="dashboard-sections">
        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h2 className="dashboard-card-title">Your Brands</h2>
            <button className="dashboard-link-btn" onClick={() => navigate("/brands")}>
              View all
            </button>
          </div>
          {data.brands.length === 0 ? (
            <p className="dashboard-empty">No brands yet.</p>
          ) : (
            <div className="dashboard-brands">
              {data.brands.map((brand) => (
                <div
                  key={brand.brandId}
                  className="dashboard-brand-item"
                  onClick={() => navigate(`/brands/${brand.brandId}`)}
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
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="dashboard-card">
          <div className="dashboard-card-header">
            <h2 className="dashboard-card-title">Recent Reports</h2>
            <button className="dashboard-link-btn" onClick={() => navigate("/reports")}>
              View all
            </button>
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
